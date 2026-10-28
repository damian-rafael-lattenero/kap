package kap.ksp.fixtures.packaged

import kap.KapTypeSafe

// Regression for SPEC-4.1.0 F-09: a class INSIDE a package must get the plain
// `kap(::FixtPackaged)` entry when its constructor shape is unique. Every other
// fixture lives in the root package, which is why 4.0.0 never noticed.
@KapTypeSafe
data class FixtPackaged(val name: String, val count: Int)

@KapTypeSafe
fun packagedFn(flag: Boolean, label: String): FixtPackaged = FixtPackaged(label, if (flag) 1 else 0)

// Regression for SPEC-4.1.0 F-11: a NESTED annotated class must be referenced by its
// real qualified name (Outer.Nested) and named by its chain (OuterNestedKap) in the
// generated code. 4.0.0 emitted `pkg.Nested`, which does not compile.
class Outer {
    @KapTypeSafe
    data class Nested(val flag: Boolean, val count: Long)
}
