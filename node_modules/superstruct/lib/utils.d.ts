import { Struct, Infer, Result, Context, Describe } from './struct';
import { Failure } from './error';
/**
 * Check if a value is a plain object.
 */
export declare function isObject(x: unknown): x is object;
/**
 * Check if a value is a plain object.
 */
export declare function isPlainObject(x: unknown): x is {
    [key: string]: any;
};
/**
 * Return a value as a printable string.
 */
export declare function print(value: any): string;
/**
 * Shifts (removes and returns) the first value from the `input` iterator.
 * Like `Array.prototype.shift()` but for an `Iterator`.
 */
export declare function shiftIterator<T>(input: Iterator<T>): T | undefined;
/**
 * Convert a single validation result to a failure.
 */
export declare function toFailure<T, S>(result: string | boolean | Partial<Failure>, context: Context, struct: Struct<T, S>, value: any): Failure | undefined;
/**
 * Convert a validation result to an iterable of failures.
 */
export declare function toFailures<T, S>(result: Result, context: Context, struct: Struct<T, S>, value: any): IterableIterator<Failure>;
/**
 * Check a value against a struct, traversing deeply into nested values, and
 * returning an iterator of failures or success.
 */
export declare function run<T, S>(value: unknown, struct: Struct<T, S>, options?: {
    path?: any[];
    branch?: any[];
    coerce?: boolean;
    mask?: boolean;
}): IterableIterator<[Failure, undefined] | [undefined, T]>;
/**
 * Assign properties from one type to another, overwriting existing.
 */
export declare type Assign<T, U> = Simplify<U & Omit<T, keyof U>>;
/**
 * A schema for enum structs.
 */
export declare type EnumSchema<T extends string | number> = {
    [K in T]: K;
};
/**
 * Check if a type is an exact match.
 */
export declare type IsMatch<T, G> = T extends G ? (G extends T ? T : never) : never;
/**
 * Check if a type is a record type.
 */
export declare type IsRecord<T> = T extends object ? string extends keyof T ? T : never : never;
/**
 * Check if a type is a tuple.
 */
export declare type IsTuple<T> = T extends [any] ? T : T extends [any, any] ? T : T extends [any, any, any] ? T : T extends [any, any, any, any] ? T : T extends [any, any, any, any, any] ? T : never;
/**
 * Check if a type is a union.
 */
export declare type IsUnion<T, U extends T = T> = (T extends any ? (U extends T ? false : true) : false) extends false ? never : T;
/**
 * A schema for object structs.
 */
export declare type ObjectSchema = Record<string, Struct<any, any>>;
/**
 * Infer a type from an object struct schema.
 */
export declare type ObjectType<S extends ObjectSchema> = Simplify<Optionalize<{
    [K in keyof S]: Infer<S[K]>;
}>>;
/**
 * Omit properties from a type that extend from a specific type.
 */
export declare type OmitBy<T, V> = Omit<T, {
    [K in keyof T]: V extends Extract<T[K], V> ? K : never;
}[keyof T]>;
/**
 * Normalize properties of a type that allow `undefined` to make them optional.
 */
export declare type Optionalize<S extends object> = OmitBy<S, undefined> & Partial<PickBy<S, undefined>>;
/**
 * Transform an object schema type to represent a partial.
 */
export declare type PartialObjectSchema<S extends ObjectSchema> = {
    [K in keyof S]: Struct<Infer<S[K]> | undefined>;
};
/**
 * Pick properties from a type that extend from a specific type.
 */
export declare type PickBy<T, V> = Pick<T, {
    [K in keyof T]: V extends Extract<T[K], V> ? K : never;
}[keyof T]>;
/**
 * Simplifies a type definition to its most basic representation.
 */
export declare type Simplify<T> = T extends any[] | Date ? T : {
    [K in keyof T]: T[K];
} & {};
export declare type If<B extends Boolean, Then, Else> = B extends true ? Then : Else;
/**
 * A schema for any type of struct.
 */
export declare type StructSchema<T> = [T] extends [string] ? [T] extends [IsMatch<T, string>] ? null : [T] extends [IsUnion<T>] ? EnumSchema<T> : T : [T] extends [number] ? [T] extends [IsMatch<T, number>] ? null : [T] extends [IsUnion<T>] ? EnumSchema<T> : T : [T] extends [boolean] ? [T] extends [IsMatch<T, boolean>] ? null : T : T extends bigint | symbol | undefined | null | Function | Date | Error | RegExp | Map<any, any> | WeakMap<any, any> | Set<any> | WeakSet<any> | Promise<any> ? null : T extends Array<infer E> ? T extends IsTuple<T> ? null : Struct<E> : T extends object ? T extends IsRecord<T> ? null : {
    [K in keyof T]: Describe<T[K]>;
} : null;
/**
 * A schema for tuple structs.
 */
export declare type TupleSchema<T> = {
    [K in keyof T]: Struct<T[K]>;
};
//# sourceMappingURL=utils.d.ts.map