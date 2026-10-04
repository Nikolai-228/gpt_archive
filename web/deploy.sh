#!/usr/bin/env bash

set -xe

if [ -d build/ ]; then
    rm -r build/
fi
NODE_ENV=production yarn build

FILES_TO_COPY=(
    build static package.json yarn.lock
)

tmp=$(mktemp -d)
tar -c ${FILES_TO_COPY[*]} | tar -C $tmp -x
cd $tmp
yarn install --prod

tar -cJ . | \
    ssh archive@archive.istu.ru "tar -C /home/archive/istu-photo-archive/web -xJv"
