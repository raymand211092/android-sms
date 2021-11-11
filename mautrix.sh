#!/bin/bash

set -e

ANDROID_API=23
NDK_VERSION=21.3.6528147
OLM_VERSION=3.2.4

if [ -z "$ANDROID_HOME" ]; then
	echo "ANDROID_HOME not set"
	exit 1
fi

NDK_ROOT="$ANDROID_HOME/ndk/$NDK_VERSION"
if [ ! -d "$NDK_ROOT" ]; then
	echo "Missing Android NDK"
	exit 1
fi

if [ "$(uname)" = 'Darwin' ]; then
  NDK_ARCH="darwin-x86_64"
else
  NDK_ARCH="linux-x86_64"
fi

OLM=olm-$OLM_VERSION
AAR=$OLM.aar

curl -L https://gitlab.matrix.org/matrix-org/olm/-/archive/$OLM_VERSION/$OLM.tar.gz -o - | tar -xvzf - $OLM/include/olm
curl -L https://jitpack.io/org/matrix/gitlab/matrix-org/olm/$OLM_VERSION/$AAR -o "$OLM/$AAR"

LDFLAGS="-X main.Tag=$(git describe --exact-match --tags 2>/dev/null) -X main.Commit=$(git rev-parse HEAD) -X 'main.BuildTime=$(date '+%b %_d %Y, %H:%M:%S')'"
ROOT="$(pwd)"

build_mautrix() {
  local ANDROID_ARCH=$1
  local NDK_TARGET=$2
  local GOARCH=$3
  local GOARM=$4
  local LIB_JNI="$ROOT/sms/src/main/jniLibs/$ANDROID_ARCH"
  local APP_JNI="$ROOT/app/src/main/jniLibs/$ANDROID_ARCH"
  echo
  echo "Building $ANDROID_ARCH"
  echo

  rm -rf $LIB_JNI $APP_JNI
  mkdir -p $LIB_JNI $APP_JNI
  unzip -jq -o "$ROOT/$OLM/$AAR" "jni/$ANDROID_ARCH/libolm.so" -d $APP_JNI

  (set -x; CGO_CFLAGS="-I$ROOT/$OLM/include/" CGO_LDFLAGS="-L $LIB_JNI -L $APP_JNI -lm -llog" CGO_ENABLED=1 \
  GOOS=android GOARCH=$GOARCH GOARM=$GOARM \
  CC="$NDK_ROOT/toolchains/llvm/prebuilt/$NDK_ARCH/bin/$NDK_TARGET$ANDROID_API-clang" \
  go build -ldflags "$LDFLAGS" -o $LIB_JNI/libmautrix.so)

  cp "$NDK_ROOT/sources/cxx-stl/llvm-libc++/libs/$ANDROID_ARCH/libc++_shared.so" $APP_JNI
}

git submodule update --init

pushd mautrix-imessage || exit

git checkout go.mod

build_mautrix armeabi-v7a armv7a-linux-androideabi arm 7
build_mautrix arm64-v8a aarch64-linux-android arm64
build_mautrix x86 i686-linux-android 386
build_mautrix x86_64 x86_64-linux-android amd64

popd
