
openssl genrsa -out key.pem 2048

openssl rsa -in key.pem -pubout > key.pub

# openssl pkeyutl -sign -in hash -inkey key.pem -pkeyopt digest:sha256 -keyform PEM -out data.zip.sign


echo -n "c8be1b8f4d60d99c281fc2db75e0f56df42a83ad2f0b091621ce19357e19d853" | xxd -r -p | openssl pkeyutl -sign -inkey key.pem -pkeyopt digest:sha256 -keyform PEM -out imageid.sign

# cat imageid.sign | xxd -p