# Third-Party Notices

Covalent is distributed under the Mozilla Public License 2.0 (see `LICENSE`). This file lists the
third-party material it bundles, and the third-party libraries it depends on.

## Bundled in this repository

### Embedded fonts

- Location: `covalent-supervision/src/main/resources/org/opales/covalent/supervision/supervisionhmi/view/impl/fonts`
- IBM Plex Sans - Copyright © 2017 IBM Corp., with Reserved Font Name "Plex" (https://github.com/IBM/plex)
- Sora - Copyright 2019 The Sora Project Authors (https://github.com/sora-xor/sora-font)
- JetBrains Mono - Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)
- License: SIL Open Font License, Version 1.1

Each font's license text is preserved beside it, in the same folder.

### Maven Wrapper

- Files: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- Apache Maven Wrapper, The Apache Software Foundation (https://maven.apache.org/tools/wrapper/)
- License: Apache License, Version 2.0 (the license notice is kept in the head of each script)

## Dependencies, not bundled

These libraries are not copied into this repository. Maven downloads them at build time, and they
remain under their own licenses. The table lists the direct dependencies and what Maven brings with
them (compile scope, as resolved with OpenJFX 21.0.12, ELK 0.9.1 and Guava 33.7.2-jre).

| Library | Used by | License |
| --- | --- | --- |
| OpenJFX (`javafx-controls`, `javafx-web`), with `javafx-graphics`, `javafx-base`, `javafx-media` | `covalent-hmi`, `covalent-supervision`, `covalent-examples` | GPL v2 with the Classpath Exception |
| Eclipse Layout Kernel (`org.eclipse.elk.core`, `org.eclipse.elk.alg.layered`), with `org.eclipse.elk.graph` and `org.eclipse.elk.alg.common` | `covalent-supervision` | Eclipse Public License 2.0 |
| Eclipse Modeling Framework (`org.eclipse.emf.common`, `org.eclipse.emf.ecore`, `org.eclipse.emf.ecore.xmi`), brought by ELK | `covalent-supervision` | Eclipse Public License 1.0 |
| Guava (`guava`, `failureaccess`, `listenablefuture`), brought by ELK | `covalent-supervision` | Apache License 2.0 |
| JSpecify, Error Prone annotations, J2ObjC annotations, brought by Guava | `covalent-supervision` | Apache License 2.0 |
| JUnit 4 | tests only | Eclipse Public License 1.0 |

`covalent-core` has no third-party dependency.
