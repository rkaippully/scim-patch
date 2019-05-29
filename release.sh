#!/bin/bash
#
# Usage:
#
#   ./release.sh :major
#   ./release.sh :minor
#   ./release.sh :patch
#

lein release $1 && lein deploy releases && lein vcs push && echo "Project was successfully released."
