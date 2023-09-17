import { Struct, Refiner } from '../struct';
/**
 * Ensure that a string, array, map, or set is empty.
 */
export declare function empty<T extends string | any[] | Map<any, any> | Set<any>, S extends any>(struct: Struct<T, S>): Struct<T, S>;
/**
 * Ensure that a number or date is below a threshold.
 */
export declare function max<T extends number | Date, S extends any>(struct: Struct<T, S>, threshold: T, options?: {
    exclusive?: boolean;
}): Struct<T, S>;
/**
 * Ensure that a number or date is above a threshold.
 */
export declare function min<T extends number | Date, S extends any>(struct: Struct<T, S>, threshold: T, options?: {
    exclusive?: boolean;
}): Struct<T, S>;
/**
 * Ensure that a string matches a regular expression.
 */
export declare function pattern<T extends string, S extends any>(struct: Struct<T, S>, regexp: RegExp): Struct<T, S>;
/**
 * Ensure that a string, array, number, date, map, or set has a size (or length, or time) between `min` and `max`.
 */
export declare function size<T extends string | number | Date | any[] | Map<any, any> | Set<any>, S extends any>(struct: Struct<T, S>, min: number, max?: number): Struct<T, S>;
/**
 * Augment a `Struct` to add an additional refinement to the validation.
 *
 * The refiner function is guaranteed to receive a value of the struct's type,
 * because the struct's existing validation will already have passed. This
 * allows you to layer additional validation on top of existing structs.
 */
export declare function refine<T, S>(struct: Struct<T, S>, name: string, refiner: Refiner<T>): Struct<T, S>;
//# sourceMappingURL=refinements.d.ts.map