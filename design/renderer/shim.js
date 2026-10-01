/*
 * shim.js — minimal stand-in for the claude.ai/design canvas runtime (support.js).
 *
 * Enough to mount a *.dc.html component offline:
 *   <x-dc> <helmet>…</helmet> <template root…> </x-dc>
 *   <script type="text/x-dc" data-dc-script data-props="{…}"> class Component extends DCLogic {…} </script>
 *
 * Provides:
 *   window.DCLogic            React.Component subclass; render() = template(renderVals()).
 *   window.DC.load(url)       fetch + parse a .dc.html, apply its <helmet>, compile template + script.
 *   window.DC.mount(C, props, el)
 *   window.DC.settle()        resolves when fonts and <img>s are done.
 *   window.__dcErrors         every compile / eval / render / resource problem, as strings.
 *
 * Template language (as used by the prototype):
 *   {{ expr }}                          evaluated with(scope) against renderVals() + loop vars
 *   <sc-if value="{{ c }}">…</sc-if>    children when truthy (no wrapper element)
 *   <sc-for list="{{ xs }}" as="x">     children repeated per item (no wrapper element)
 *   attr="{{ e }}"                      raw value (function, bool, number, object, element)
 *   attr="a {{ e }} b"                  string interpolation
 *   style="css text"                    -> React style object
 *   hint-*                              ignored (canvas editor hints)
 */
(function () {
  'use strict';
  var R = window.React;
  if (!R || !window.ReactDOM) throw new Error('shim.js needs React + ReactDOM UMD loaded first');

  // ---------------------------------------------------------------- diagnostics
  var errors = (window.__dcErrors = window.__dcErrors || []);
  function report(kind, msg) {
    var line = kind + ': ' + msg;
    if (errors.indexOf(line) < 0) errors.push(line);
    try { console.error('[dc-shim] ' + line); } catch (e) { /* ignore */ }
  }

  // ------------------------------------------------ helmet URL rewrites (offline)
  // External resources the prototype helmet asks for, mapped to local copies.
  var REWRITES = [
    [/^https?:\/\/unpkg\.com\/lucide@[^/]+\/dist\/umd\/lucide(\.min)?\.js(\?.*)?$/, '/vendor/lucide.min.js'],
    [/^https?:\/\/fonts\.googleapis\.com\/css2?\?/, '/fonts.css'],
  ];
  function rewriteUrl(u, base) {
    if (!u) return u;
    for (var i = 0; i < REWRITES.length; i++) if (REWRITES[i][0].test(u)) return REWRITES[i][1];
    return new URL(u, base).href;
  }

  // ----------------------------------------------------------- expressions
  var exprCache = new Map();
  function compileExpr(src) {
    var f = exprCache.get(src);
    if (!f) {
      try {
        // Function-constructor bodies are sloppy mode, so `with` is allowed here.
        f = new Function('scope', 'with (scope) { return (' + src + '\n); }');
      } catch (e) {
        report('compile', '{{ ' + src + ' }} -> ' + e.message);
        f = function () { return undefined; };
      }
      exprCache.set(src, f);
    }
    return f;
  }
  function evalExpr(src, scope) {
    try { return compileExpr(src)(scope); }
    catch (e) { report('eval', '{{ ' + src + ' }} -> ' + e.message); return undefined; }
  }

  var BIND = /\{\{([\s\S]*?)\}\}/g;
  var SINGLE = /^\s*\{\{([\s\S]*?)\}\}\s*$/;
  function parseParts(str) {
    var parts = [], last = 0, m;
    BIND.lastIndex = 0;
    while ((m = BIND.exec(str))) {
      if (m.index > last) parts.push(str.slice(last, m.index));
      parts.push({ expr: m[1].trim() });
      last = BIND.lastIndex;
    }
    if (last < str.length) parts.push(str.slice(last));
    return parts;
  }
  // attribute value -> { single: expr } | { parts: [...] } | { text: '...' }
  function parseAttrValue(v) {
    if (v.indexOf('{{') < 0) return { text: v };
    var m = SINGLE.exec(v);
    if (m && m[1].indexOf('{{') < 0 && m[1].indexOf('}}') < 0) return { single: m[1].trim() };
    return { parts: parseParts(v) };
  }
  function interp(parts, scope) {
    var s = '';
    for (var i = 0; i < parts.length; i++) {
      var p = parts[i];
      if (typeof p === 'string') { s += p; continue; }
      var v = evalExpr(p.expr, scope);
      if (v != null && v !== false) s += String(v);
    }
    return s;
  }

  // ------------------------------------------------------------------ style
  function splitDecls(css) {
    var out = [], depth = 0, quote = null, start = 0;
    for (var i = 0; i < css.length; i++) {
      var ch = css[i];
      if (quote) { if (ch === '\\') i++; else if (ch === quote) quote = null; continue; }
      if (ch === '"' || ch === "'") quote = ch;
      else if (ch === '(') depth++;
      else if (ch === ')') depth = Math.max(0, depth - 1);
      else if (ch === ';' && depth === 0) { out.push(css.slice(start, i)); start = i + 1; }
    }
    out.push(css.slice(start));
    return out;
  }
  function cssPropToReact(p) {
    if (p.indexOf('--') === 0) return p;                  // custom property: as-is
    p = p.toLowerCase();
    if (p.indexOf('-ms-') === 0) p = p.slice(1);          // React: msTransform (lowercase ms)
    return p.replace(/-([a-z])/g, function (_, c) { return c.toUpperCase(); }); // -webkit-x -> WebkitX
  }
  function styleToObject(css) {
    var o = {};
    var decls = splitDecls(String(css));
    for (var i = 0; i < decls.length; i++) {
      var d = decls[i], k = d.indexOf(':');
      if (k < 0) { if (d.trim()) report('style', 'unparsable declaration "' + d.trim() + '"'); continue; }
      var prop = d.slice(0, k).trim(), val = d.slice(k + 1).trim();
      if (!prop || val === '') continue;
      if (/!important$/i.test(val)) report('style', '!important is not supported in React inline styles: ' + prop);
      o[cssPropToReact(prop)] = val;
    }
    return o;
  }

  // ------------------------------------------------------ attribute names
  // The HTML parser lower-cases attribute names; map them back to React prop names.
  var REACT_PROP = { 'class': 'className', 'for': 'htmlFor', 'xlink:href': 'xlinkHref', 'xml:space': 'xmlSpace', 'xml:lang': 'xmlLang' };
  ('onClick onDoubleClick onChange onInput onSubmit onKeyDown onKeyUp onKeyPress onFocus onBlur onScroll onWheel ' +
   'onMouseDown onMouseUp onMouseMove onMouseEnter onMouseLeave onMouseOver onMouseOut onContextMenu ' +
   'onPointerDown onPointerUp onPointerMove onPointerEnter onPointerLeave onPointerCancel ' +
   'onTouchStart onTouchMove onTouchEnd onDragStart onDragOver onDrop onLoad onError onAnimationEnd onTransitionEnd ' +
   'autoFocus autoComplete autoCapitalize autoPlay maxLength minLength readOnly tabIndex spellCheck crossOrigin srcSet ' +
   'contentEditable colSpan rowSpan encType inputMode enterKeyHint formAction noValidate allowFullScreen frameBorder ' +
   'referrerPolicy playsInline defaultValue defaultChecked accessKey dateTime hrefLang useMap charSet acceptCharset httpEquiv')
    .split(/\s+/).forEach(function (n) { REACT_PROP[n.toLowerCase()] = n; });
  var SVG_NS = 'http://www.w3.org/2000/svg';
  function propName(name, isSvg) {
    if (REACT_PROP[name]) return REACT_PROP[name];
    if (name.indexOf('aria-') === 0 || name.indexOf('data-') === 0) return name;
    if (isSvg && name.indexOf('-') > 0) return name.replace(/-([a-z])/g, function (_, c) { return c.toUpperCase(); });
    return name;
  }

  // ---------------------------------------------------- template compile
  // DOM -> small AST, compiled once per component.
  function compileChildren(nodes) {
    var out = [];
    for (var i = 0; i < nodes.length; i++) { var c = compileNode(nodes[i]); if (c) out.push(c); }
    return out;
  }
  function compileNode(node) {
    if (node.nodeType === 3) {
      var t = node.nodeValue;
      return t.indexOf('{{') < 0 ? { t: 'text', v: t } : { t: 'bound', parts: parseParts(t) };
    }
    if (node.nodeType !== 1) return null; // comments, PIs
    var tag = node.localName;
    if (tag === 'sc-if') {
      var cond = parseAttrValue(node.getAttribute('value') || '');
      return { t: 'if', cond: cond, kids: compileChildren(node.childNodes) };
    }
    if (tag === 'sc-for') {
      return { t: 'for', list: parseAttrValue(node.getAttribute('list') || ''), as: node.getAttribute('as') || 'item',
               index: node.getAttribute('index') || null, kids: compileChildren(node.childNodes) };
    }
    if (tag.indexOf('sc-') === 0) report('template', 'unsupported element <' + tag + '> rendered as a plain element');
    var isSvg = node.namespaceURI === SVG_NS;
    var attrs = [];
    for (var i = 0; i < node.attributes.length; i++) {
      var a = node.attributes[i];
      if (a.name.indexOf('hint-') === 0) continue;
      attrs.push({ name: propName(a.name, isSvg), raw: a.name, v: parseAttrValue(a.value) });
    }
    // <textarea> content is its default value; React wants value= instead of children.
    var kids = (tag === 'textarea') ? [] : compileChildren(node.childNodes);
    return { t: 'el', tag: tag, svg: isSvg, attrs: attrs, kids: kids };
  }

  // ----------------------------------------------------- template render
  function toChild(v, where) {
    if (v == null || typeof v === 'boolean') return null;
    if (typeof v === 'string' || typeof v === 'number') return v;
    if (R.isValidElement(v)) return v;
    if (Array.isArray(v)) return v.map(function (x) { return toChild(x, where); });
    report('render', 'non-renderable object in ' + where + ': ' + Object.prototype.toString.call(v));
    return null;
  }
  function attrValue(a, scope) {
    if ('text' in a.v) return a.v.text;
    if ('single' in a.v) return evalExpr(a.v.single, scope);
    return interp(a.v.parts, scope);
  }
  function childScope(scope, name, value, idxName, idx) {
    var s = Object.create(scope);
    s[name] = value;
    if (idxName) s[idxName] = idx;
    return s;
  }
  function renderList(nodes, scope) {
    var out = new Array(nodes.length);
    for (var i = 0; i < nodes.length; i++) out[i] = renderNode(nodes[i], scope, i);
    return out;
  }
  function renderNode(n, scope, key) {
    switch (n.t) {
      case 'text': return n.v;
      case 'bound': {
        var parts = n.parts.map(function (p) { return typeof p === 'string' ? p : toChild(evalExpr(p.expr, scope), '{{ ' + p.expr + ' }}'); });
        if (parts.every(function (p) { return typeof p === 'string' || typeof p === 'number' || p == null; }))
          return parts.map(function (p) { return p == null ? '' : String(p); }).join('');
        return R.createElement.apply(null, [R.Fragment, { key: key }].concat(parts));
      }
      case 'if': {
        var c = 'text' in n.cond ? n.cond.text : ('single' in n.cond ? evalExpr(n.cond.single, scope) : interp(n.cond.parts, scope));
        if (!c) return null;
        return R.createElement.apply(null, [R.Fragment, { key: key }].concat(renderList(n.kids, scope)));
      }
      case 'for': {
        var list = 'single' in n.list ? evalExpr(n.list.single, scope) : undefined;
        if (list == null) return null;
        if (!Array.isArray(list)) {
          if (typeof list[Symbol.iterator] === 'function') list = Array.from(list);
          else { report('render', 'sc-for list is not an array: ' + JSON.stringify(n.list)); return null; }
        }
        var items = list.map(function (item, i) {
          var s = childScope(scope, n.as, item, n.index, i);
          return R.createElement.apply(null, [R.Fragment, { key: i }].concat(renderList(n.kids, s)));
        });
        return R.createElement(R.Fragment, { key: key }, items);
      }
      case 'el': {
        var props = { key: key };
        for (var i = 0; i < n.attrs.length; i++) {
          var a = n.attrs[i], v = attrValue(a, scope);
          if (a.name === 'style') {
            if (v == null || v === false || v === '') continue;
            props.style = typeof v === 'object' ? v : styleToObject(v);
            continue;
          }
          if (/^on[A-Z]/.test(a.name)) {
            if (typeof v === 'function') props[a.name] = v;
            else if (v != null) report('render', a.raw + ' on <' + n.tag + '> is not a function');
            continue;
          }
          if (v === undefined || v === null) continue;
          props[a.name] = v;
        }
        return R.createElement.apply(null, [n.tag, props].concat(renderList(n.kids, scope)));
      }
    }
    return null;
  }
  function renderTemplate(tpl, vals) {
    var scope = Object.create(vals && typeof vals === 'object' ? vals : null);
    var kids = renderList(tpl, scope);
    return R.createElement.apply(null, [R.Fragment, null].concat(kids));
  }

  // --------------------------------------------------------------- DCLogic
  class DCLogic extends R.Component {
    constructor(props) {
      super(props);
      this.props = props;
      if (this.state == null) this.state = {};
    }
    render() {
      var tpl = this.constructor.__dcTemplate;
      if (!tpl) throw new Error('DCLogic: component has no compiled template');
      var vals;
      try { vals = typeof this.renderVals === 'function' ? this.renderVals() : {}; }
      catch (e) { report('renderVals', (e && e.stack) || String(e)); throw e; }
      return renderTemplate(tpl, vals);
    }
  }
  window.DCLogic = DCLogic;

  // Visible error overlay so nothing fails silently in a screenshot.
  class DCErrorBoundary extends R.Component {
    constructor(p) { super(p); this.state = { err: null }; }
    static getDerivedStateFromError(err) { return { err: err }; }
    componentDidCatch(err, info) { report('react', ((err && err.stack) || String(err)) + (info && info.componentStack ? '\n' + info.componentStack : '')); }
    render() {
      if (!this.state.err) return this.props.children;
      return R.createElement('pre', { 'data-dc-error': '1', style: { position: 'fixed', inset: 0, margin: 0, padding: 16, background: '#b00020', color: '#fff', font: '12px/1.4 monospace', whiteSpace: 'pre-wrap', zIndex: 99999 } },
        'DC runtime error\n\n' + ((this.state.err && this.state.err.stack) || String(this.state.err)));
    }
  }

  // ---------------------------------------------------------------- helmet
  function applyHelmet(helmet, base) {
    var loads = [];
    if (!helmet) return Promise.resolve();
    Array.prototype.forEach.call(helmet.children, function (el) {
      var tag = el.localName;
      if (tag === 'meta') {
        var name = el.getAttribute('name');
        if (name === 'viewport' && document.querySelector('meta[name=viewport]')) return;
        var m = document.createElement('meta');
        Array.prototype.forEach.call(el.attributes, function (a) { m.setAttribute(a.name, a.value); });
        document.head.appendChild(m);
      } else if (tag === 'link') {
        var rel = (el.getAttribute('rel') || '').toLowerCase();
        if (rel === 'preconnect' || rel === 'dns-prefetch') return; // network hints only
        var l = document.createElement('link');
        Array.prototype.forEach.call(el.attributes, function (a) { if (a.name !== 'href') l.setAttribute(a.name, a.value); });
        l.href = rewriteUrl(el.getAttribute('href'), base);
        if (rel === 'stylesheet') loads.push(new Promise(function (res) {
          l.onload = res;
          l.onerror = function () { report('helmet', 'stylesheet failed: ' + l.href); res(); };
        }));
        document.head.appendChild(l);
      } else if (tag === 'style') {
        var s = document.createElement('style');
        s.textContent = el.textContent;
        document.head.appendChild(s);
      } else if (tag === 'script') {
        var sc = document.createElement('script');
        var src = el.getAttribute('src');
        if (src) {
          sc.src = rewriteUrl(src, base);
          sc.async = false;
          loads.push(new Promise(function (res) {
            sc.onload = res;
            sc.onerror = function () { report('helmet', 'script failed: ' + sc.src); res(); };
          }));
        } else sc.textContent = el.textContent;
        document.head.appendChild(sc);
      } else if (tag === 'title') {
        document.title = el.textContent;
      } else {
        report('helmet', 'ignored <' + tag + '>');
      }
    });
    return Promise.all(loads);
  }

  // ------------------------------------------------------------------ load
  var cache = new Map();
  function load(url) {
    var abs = new URL(url, location.href).href;
    if (cache.has(abs)) return cache.get(abs);
    var p = fetch(abs, { cache: 'no-store' }).then(function (r) {
      if (!r.ok) throw new Error('fetch ' + abs + ' -> HTTP ' + r.status);
      return r.text();
    }).then(function (html) {
      var doc = new DOMParser().parseFromString(html, 'text/html');
      var xdc = doc.querySelector('x-dc');
      if (!xdc) throw new Error(abs + ': no <x-dc> root');
      var helmet = null, roots = [];
      Array.prototype.forEach.call(xdc.childNodes, function (n) {
        if (n.nodeType === 1 && n.localName === 'helmet') helmet = n; else roots.push(n);
      });
      var scriptEl = doc.querySelector('script[type="text/x-dc"]');
      if (!scriptEl) throw new Error(abs + ': no <script type="text/x-dc">');
      var spec = {};
      try { spec = JSON.parse(scriptEl.getAttribute('data-props') || '{}'); }
      catch (e) { report('props', 'bad data-props JSON: ' + e.message); }
      var defaults = {};
      Object.keys(spec).forEach(function (k) { if (spec[k] && 'default' in spec[k]) defaults[k] = spec[k]['default']; });
      var tpl = compileChildren(roots);
      return applyHelmet(helmet, abs).then(function () {
        var Component;
        try {
          Component = new Function('DCLogic', 'React', 'ReactDOM',
            scriptEl.textContent + '\n;return (typeof Component !== "undefined") ? Component : undefined;')(DCLogic, R, window.ReactDOM);
        } catch (e) { report('script', (e && e.stack) || String(e)); throw e; }
        if (typeof Component !== 'function') throw new Error(abs + ': script did not define class Component');
        Component.__dcTemplate = tpl;
        Component.__dcSource = abs;
        return { Component: Component, defaults: defaults, spec: spec, template: tpl };
      });
    });
    cache.set(abs, p);
    return p;
  }

  // ----------------------------------------------------------------- mount
  // Props: declared defaults (data-props) overlaid with the frame's attributes.
  function mount(loaded, props, container) {
    var all = Object.assign({}, loaded.defaults, props || {});
    var root = window.ReactDOM.createRoot(container);
    window.ReactDOM.flushSync(function () {
      root.render(R.createElement(DCErrorBoundary, null, R.createElement(loaded.Component, all)));
    });
    return { root: root, props: all };
  }

  function nextFrame() { return new Promise(function (r) { requestAnimationFrame(function () { requestAnimationFrame(r); }); }); }
  function settle() {
    return nextFrame().then(function () {
      var faces = [];
      document.fonts.forEach(function (f) { faces.push(f.load().catch(function (e) { report('font', f.family + ' ' + f.weight + ': ' + e); })); });
      return Promise.all(faces);
    }).then(function () { return document.fonts.ready; }).then(function () {
      var imgs = Array.prototype.slice.call(document.images);
      return Promise.all(imgs.map(function (img) {
        if (img.complete) return null;
        return new Promise(function (r) { img.addEventListener('load', r, { once: true }); img.addEventListener('error', r, { once: true }); });
      }));
    }).then(function () {
      return Promise.all(Array.prototype.slice.call(document.images).map(function (img) {
        return img.naturalWidth ? img.decode().catch(function () {}) : null;
      }));
    }).then(nextFrame);
  }

  window.DC = { load: load, mount: mount, settle: settle, report: report, styleToObject: styleToObject, rewriteUrl: rewriteUrl };
})();
