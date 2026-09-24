#!/usr/bin/env bash
# Publishes the tour's screenshots and the generated screen map to the orphan
# `screenshots` branch. Keeps whatever else the branch holds (design renders).
set -euo pipefail

BRANCH=${SCREENS_BRANCH:-screenshots}
TOUR=${TOUR_OUT:-build/screen-tour}
WORK=$(mktemp -d)

if [ ! -f "$TOUR/tour/manifest.json" ]; then
  echo "No tour output in $TOUR/tour; leaving $BRANCH untouched."
  exit 0
fi

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

publish() {
  rm -rf "$WORK" && mkdir -p "$WORK"
  git worktree prune
  if git ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
    git fetch --depth=1 origin "$BRANCH"
    git worktree add --detach "$WORK" FETCH_HEAD
  else
    git worktree add --detach "$WORK"
    git -C "$WORK" checkout --orphan "$BRANCH-new"
    git -C "$WORK" rm -rf --quiet .
  fi

  rm -rf "$WORK/app"
  mkdir -p "$WORK/app"
  if [ -d "$TOUR/tour" ]; then cp -R "$TOUR/tour/." "$WORK/app/"; fi
  if [ -f "$TOUR/instrument.txt" ]; then cp "$TOUR/instrument.txt" "$WORK/app/"; fi

  python3 scripts/ci/screens_map.py \
    --root "$WORK" \
    --commit "$SOURCE_SHA" \
    --branch "$SOURCE_BRANCH" \
    --run-url "$RUN_URL" \
    --outcome "$TOUR_OUTCOME"

  git -C "$WORK" add -A
  if git -C "$WORK" diff --cached --quiet; then
    echo "Nothing changed on $BRANCH."
    return 0
  fi
  git -C "$WORK" commit --quiet -m "Screen tour of ${SOURCE_BRANCH}@${SOURCE_SHA:0:7}" \
    -m "Captured by ${RUN_URL}"
  git -C "$WORK" push origin "HEAD:refs/heads/$BRANCH"
}

# One retry covers a concurrent push to the branch.
publish || { echo "Publish failed, retrying on the latest $BRANCH"; sleep 5; publish; }
git worktree remove --force "$WORK" || true
