
BAZEL_CONTAINER_IMAGE_TARGET_NAME="determ-docker-test"
IMAGE_TAG="bazel:${BAZEL_CONTAINER_IMAGE_TARGET_NAME}"

IMAGE_ID="$(docker image inspect ${IMAGE_TAG} -f '{{.Id}}')"
# Format of "Id" is sha256:00112233... etc, so strip a way the first 7 chars to get the actual sha256-hash as hex
IMAGE_ID_HASH=${IMAGE_ID:7}
echo Image-ID hash = $IMAGE_ID_HASH

echo "Pre-Verifying the signature over ImageID = ${IMAGE_ID_HASH} using key.pub"
echo -n $IMAGE_ID_HASH | xxd -r -p | openssl pkeyutl -verify -pubin -inkey key.pub -sigfile imageid.sign -pkeyopt digest:sha256 -keyform PEM

if [ $? -eq 0 ]
then
  echo "Signature verified with the present public key. If same public key is also registered at deployment-agent, deployment should succeed."
  exit 0
fi

docker image inspect ${IMAGE_TAG}

echo "app.jar = "
jar -tfv build/libs/app.jar
ls -l build/libs/app.jar
echo "SHA256 over app.jar = "
shasum -a 256 build/libs/app.jar
java --version
./gradlew --version

echo "Signature verification failed. No reason to continue with deploy. Compare image-info and app.jar SHA256 above with local image for debugging."
exit 1
