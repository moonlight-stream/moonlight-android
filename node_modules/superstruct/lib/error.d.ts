/**
 * A `StructFailure` represents a single specific failure in validation.
 */
export declare type Failure = {
    value: any;
    key: any;
    type: string;
    refinement: string | undefined;
    message: string;
    branch: Array<any>;
    path: Array<any>;
};
/**
 * `StructError` objects are thrown (or returned) when validation fails.
 *
 * Validation logic is design to exit early for maximum performance. The error
 * represents the first error encountered during validation. For more detail,
 * the `error.failures` property is a generator function that can be run to
 * continue validation and receive all the failures in the data.
 */
export declare class StructError extends TypeError {
    value: any;
    key: any;
    type: string;
    refinement: string | undefined;
    path: Array<any>;
    branch: Array<any>;
    failures: () => Array<Failure>;
    [x: string]: any;
    constructor(failure: Failure, failures: () => Generator<Failure>);
}
//# sourceMappingURL=error.d.ts.map