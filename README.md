QR4J
====

What is this?
-------------
QR4J is a Java library for generating
[QR codes](https://en.wikipedia.org/wiki/QR_code). QR4J is intended to
be:

* self-contained: no network API hits to any external services; and

* minimally-dependent: using QR4J should not involve pulling in a
  plethora of JARs, and ideally none at all.

QR4J is _not_ intended to:

* _read_ QR codes; or

* generate _any other type_ of barcode.

Getting started
---------------
Generating a QR code is this simple:

```
QrCode qr = QrCode.encodeText("Hello, world!", QrCode.Ecc.LOW);
BufferedImage img = qr.toImage(10, 4);
File imgFile = new File("hello-world-QR.png");
ImageIO.write(img, "png", imgFile);
```

Using QR4J
----------
Right now, we're still at `0.1-SNAPSHOT`, but as soon as 1.0 is ready,
it will ship to Maven Central. Stay tuned.

References
----------
QR4J is _heavily_ based on
[QR Code generator library](https://github.com/nayuki/QR-Code-generator),
specifically the `io.nayuki.fastqrcodegen` package.

* _Why not fork that project?_ Because the repository includes
  implementations in languages we're not interested in.

* _Why not use that project's Java release?_ Because it doesn't appear
  that the `io.nayuki.fastqrcodegen` package is even released as a JAR
  available on Maven Central.
