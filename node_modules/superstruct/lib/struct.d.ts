import { StructSchema } from './utils';
import { StructError, Failure } from './error';
/**
 * `Struct` objects encapsulate the validation logic for a specific type of
 * values. Once constructed, you use the `assert`, `is` or `validate` helpers to
 * validate unknown input data against the struct.
 */
export declare class Struct<T = unknown, S = unknown> {
    readonly TYPE: T;
    type: string;
    schema: S;
    coercer: (value: unknown, context: Context) => unknown;
    validator: (value: unknown, context: Context) => Iterable<Failure>;
    refiner: (value: T, context: Context) => Iterable<Failure>;
    entries: (value: unknown, context: Context) => Iterable<[string | number, unknown, Struct<any> | Struct<never>]>;
    constructor(props: {
        type: string;
        schema: S;
        coercer?: Coercer;
        validator?: Validator;
        refiner?: Refiner<T>;
        entries?: Struct<T, S>['entries'];
    });
    /**
     * Assert that a value passes the struct's validation, throwing if it doesn't.
     */
    assert(value: unknown): asserts value is T;
    /**
     * Create a value with the struct's coercion logic, then validate it.
     */
    create(value: unknown): T;
    /**
     * Check if a value passes the struct's validation.
     */
    is(value: unknown): value is T;
    /**
     * Mask a value, coercing and validating it, but returning only the subset of
     * properties defined by the struct's schema.
     */
    mask(value: unknown): T;
    /**
     * Validate a value with the struct's validation logic, returning a tuple
     * representing the result.
     *
     * You may optionally pass `true` for the `withCoercion` argument to coerce
     * the value before attempting to validate it. If you do, the result will
     * contain the coerced result when successful.
     */
    validate(value: unknown, options?: {
        coerce?: boolean;
    }): [StructError, undefined] | [undefined, T];
}
/**
 * Assert that a value passes a struct, throwing if it doesn't.
 */
export declare function assert<T, S>(value: unknown, struct: Struct<T, S>): asserts value is T;
/**
 * Create a value with the coercion logic of struct and validate it.
 */
export declare function create<T, S>(value: unknown, struct: Struct<T, S>): T;
/**
 * Mask a value, returning only the subset of properties defined by a struct.
 */
export declare function mask<T, S>(value: unknown, struct: Struct<T, S>): T;
/**
 * Check if a value passes a struct.
 */
export declare function is<T, S>(value: unknown, struct: Struct<T, S>): value is T;
/**
 * Validate a value against a struct, returning an error if invalid, or the
 * value (with potential coercion) if valid.
 */
export declare function validate<T, S>(value: unknown, struct: Struct<T, S>, options?: {
    coerce?: boolean;
    mask?: boolean;
}): [StructError, undefined] | [undefined, T];
/**
 * A `Context` contains information about the current location of the
 * validation inside the initial input value.
 */
export declare type Context = {
    branch: Array<any>;
    path: Array<any>;
};
/**
 * A type utility to extract the type from a `Struct` class.
 */
export declare type Infer<T extends Struct<any, any>> = T['TYPE'];
/**
 * A type utility to describe that a struct represents a TypeScript type.
 */
export declare type Describe<T> = Struct<T, StructSchema<T>>;
/**
 * A `Result` is returned from validation functions.
 */
export declare type Result = boolean | string | Partial<Failure> | Iterable<boolean | string | Partial<Failure>>;
/**
 * A `Coercer` takes an unknown value and optionally coerces it.
 */
export declare type Coercer<T = unknown> = (value: T, context: Context) => unknown;
/**
 * A `Validator` takes an unknown value and validates it.
 */
export declare type Validator = (value: unknown, context: Context) => Result;
/**
 * A `Refiner` takes a value of a known type and validates it against a further
 * constraint.
 */
export declare type Refiner<T> = (value: T, context: Context) => Result;
//# sourceMappingURL=struct.d.ts.map