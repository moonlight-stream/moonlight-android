import { Struct, Coercer } from '../struct';
/**
 * Augment a `Struct` to add an additional coercion step to its input.
 *
 * This allows you to transform input data before validating it, to increase the
 * likelihood that it passes validation—for example for default values, parsing
 * different formats, etc.
 *
 * Note: You must use `create(value, Struct)` on the value to have the coercion
 * take effect! Using simply `assert()` or `is()` will not use coercion.
 */
export declare function coerce<T, S, C>(struct: Struct<T, S>, condition: Struct<C, any>, coercer: Coercer<C>): Struct<T, S>;
/**
 * Augment a struct to replace `undefined` values with a default.
 *
 * Note: You must use `create(value, Struct)` on the value to have the coercion
 * take effect! Using simply `assert()` or `is()` will not use coercion.
 */
export declare function defaulted<T, S>(struct: Struct<T, S>, fallback: any, options?: {
    strict?: boolean;
}): Struct<T, S>;
/**
 * Augment a struct to trim string inputs.
 *
 * Note: You must use `create(value, Struct)` on the value to have the coercion
 * take effect! Using simply `assert()` or `is()` will not use coercion.
 */
export declare function trimmed<T, S>(struct: Struct<T, S>): Struct<T, S>;
//# sourceMappingURL=coercions.d.ts.map