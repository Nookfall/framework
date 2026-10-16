# Public framework pages

Nookfall Framework is private. The pages in this folder are the **only** framework documentation approved for the
public website: a conceptual overview with no installation instructions, coordinates or source links. The website
copies them (and `metadata.json`) with `npm run sync:docs`; it never reads anything else from this repository.

Before adding or changing a page here, check that it does not reveal unreleased internals, private repository
links, Maven coordinates or setup steps that only work with repository access. Keep `metadata.json` in step with
`docs/compatibility.md` (the website shows its tested versions).
