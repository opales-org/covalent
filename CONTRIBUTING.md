# Contributing to Covalent

Thank you for your interest in Covalent.

Covalent is maintained by one person, on a best-effort basis. There is no guaranteed response
time, and the maintainer is selective about what enters the project: a contribution can be
declined even when it is correct, if it does not fit the scope or the design of Covalent.

## Before you write code

Please open an issue first and describe what you want to change and why. This avoids work on
something that will not be accepted. A pull request that was not discussed beforehand may be
closed without a detailed explanation.

Bug reports are welcome. Please give the version of Covalent, the JDK version, and the smallest
example that reproduces the problem. For a vulnerability, follow `SECURITY.md` instead of opening
an issue.

## Making a change

- Build and run the tests with `.\mvnw clean verify` on Windows or `./mvnw clean verify` elsewhere, as
  described in `README.md`. All tests must pass and the Javadoc check must succeed.
- Add or update tests for the behavior you change.
- Keep a change focused: one subject per pull request.
- Write code, comments and documentation in English, and follow the style of the surrounding code.
- Every new source file starts with the standard license header: copy it from an existing file.

## License

Covalent is distributed under the Mozilla Public License 2.0 (see `LICENSE`). By submitting a
contribution, you agree that it is published under the same license.
