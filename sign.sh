
# Abort if anything fails:
set -e

# TODO: Sjekk om repoDigest-pinnet baseimage finnes i nyere versjon og ev. gi beskjed / krev overstyringsparameter "-asis" eller noe

BAZEL_CONTAINER_IMAGE_TARGET_NAME="determ-docker-test"

bazel build //:$BAZEL_CONTAINER_IMAGE_TARGET_NAME
bazel run //:$BAZEL_CONTAINER_IMAGE_TARGET_NAME

IMAGE_ID="$(docker image inspect bazel:${BAZEL_CONTAINER_IMAGE_TARGET_NAME} -f '{{.Id}}')"
IMAGE_ID_HASH=${IMAGE_ID:7}
echo Image-ID hash = $IMAGE_ID_HASH

SIGNATURE_FILE=imageid.sign
echo "Creating signature in ${SIGNATURE_FILE} over sha256 = ${IMAGE_ID_HASH} using key.pem"
echo -n $IMAGE_ID_HASH | xxd -r -p | openssl pkeyutl -sign -inkey key.pem -pkeyopt digest:sha256 -keyform PEM -out $SIGNATURE_FILE

echo "Verifying the signature we just created:"
echo -n $IMAGE_ID_HASH | xxd -r -p | openssl pkeyutl -verify -pubin -inkey key.pub -sigfile imageid.sign -pkeyopt digest:sha256 -keyform PEM
