# External reference sources

These repositories are the authoritative references for C4J Next. Pinning revisions keeps the thesis analysis and implementation reproducible after the local `reference` directory is removed.

| Project | Repository | Pinned revision | Access verified |
| --- | --- | --- | --- |
| C4J | https://github.com/C4J-Team/C4J | tag `6.0.0`, commit `c874e8ecf91b1369ca97bea27e4c3cfc060dab63` | Yes, public HTTPS access |
| dbcagent | https://gitlab.git.nrw/mathis.stein/dbcagent | commit `221b2971bdde196628488a2f07ca99b4b4fe9c0a` | Yes, authenticated SSH access |

The pinned C4J repository includes the framework source, acceptance tests, and system tests and is licensed under EPL 1.0.

The local `dbcagent` checkout identifies Mathis as the author of the pinned commit, dated 2026-03-03, with message `handle assertion errors in class invariants`. Its configured origin is `git@gitlab.git.nrw:mathis.stein/dbcagent.git`. The local checkout has a comment-only modification in `ContractUtils.java`; functional analysis used the checked-out implementation and recorded that difference.

The pinned dbcagent revision was verified through `git ls-remote git@gitlab.git.nrw:mathis.stein/dbcagent.git`. Clone it through that SSH URL when implementation work needs the source.
