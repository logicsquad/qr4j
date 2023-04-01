QR4J
====

What is this?
-------------
QR4J is a Java library for generating
[QR codes](https://en.wikipedia.org/wiki/QR_code). QR4J is intended to
be:

* Self-contained: no network API hits to any external services.

* Minimally-dependent: using QR4J should not involve pulling in a
  plethora of JARs, and ideally none at all.


References
----------
QR4J is based on
[QR Code generator library](https://github.com/nayuki/QR-Code-generator),
specifically the `io.nayuki.fastqrcodegen` package.

* _Why not fork that project?_ Because the repository includes
  implementations in languages we're not interested in.

* _Why not use that project's Java release?_ Because it doesn't appear
  that the `io.nayuki.fastqrcodegen` package is even released as a JAR
  available on Maven Central.
