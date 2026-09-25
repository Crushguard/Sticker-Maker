#!/usr/bin/env bash
# Publishes the tour's screenshots and the generated screen map to the
# `screenshots` branch as a single commit: design references (and anything
# else already there) are carried over, app/ is replaced, and history is not
# kept so old screenshot sets never pile up in clones.
set -euo pipefail

BRANCH=${SCREENS_BRANCH:-screenshots}
TOUR=${TOUR_OUT:-build/screen-tour}
WORK=$(mktemp -d)
LOCAL="publish-$BRANCH-$$"

if [ ! -f "$TOUR/tour/manifest.json" ]; then
  echo "No tour output in $TOUR/tour; leaving $BRANCH untouched."
  exit 0
fi

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

cleanup() {
  git worktree remove --force "$WORK" >/dev/null 2>&1 || true
  git branch -D "$LOCAL" >/dev/null 2>&1 || true
}
trap cleanup EXIT

git worktree prune
git worktree add --detach "$WORK" HEAD
git -C "$WORK" checkout --quiet --orphan "$LOCAL"
git -C "$WORK" rm -rf --quiet .

if git ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
  git fetch --depth=1 origin "$BRANCH"
  # FETCH_HEAD belongs to this checkout, not the worktree: pass the commit.
  git -C "$WORK" checkout "$(git rev-parse FETCH_HEAD)" -- .
fi

rm -rf "$WORK/app"
mkdir -p "$WORK/app"
cp -R "$TOUR/tour/." "$WORK/app/"
for log in instrument.txt logcat-warnings.txt; do
  if [ -f "$TOUR/$log" ]; then cp "$TOUR/$log" "$WORK/app/"; fi
done

python3 scripts/ci/screens_map.py \
  --root "$WORK" \
  --commit "$SOURCE_SHA" \
  --branch "$SOURCE_BRANCH" \
  --run-url "$RUN_URL" \
  --outcome "$TOUR_OUTCOME"

git -C "$WORK" add -A
git -C "$WORK" commit --quiet -m "Screen tour of ${SOURCE_BRANCH}@${SOURCE_SHA:0:7}" \
  -m "Captured by ${RUN_URL}"
git -C "$WORK" push --force origin "HEAD:refs/heads/$BRANCH"
