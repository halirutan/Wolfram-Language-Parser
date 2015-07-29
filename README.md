#A Parser for the Wolfram Language (Mathematica)

The code for the parser is extracted from my [Mathematica IntelliJ Plugin](https://github.com/halirutan/Mathematica-IntelliJ-Plugin).
All dependencies to the [IntelliJ Platform](http://www.jetbrains.org/pages/viewpage.action?pageId=983889) are included
and it can be used as command-line program without a running IntelliJ IDEA.

More to come but here first usage tips

- clone this repository
- open it with the free [Community Edition of IDEA](https://www.jetbrains.com/idea/)
- compile it with java 1.6.0_45
- look at the file [de.halirutan.mathematica.Main](https://github.com/halirutan/WolframLanguageParser/blob/master/wl-parser/src/de/halirutan/mathematica/Main.java).
This contains a very simple example how to read in a file, parse it and create a `FullForm`-like output by walking
through the abstract syntax tree (AST).