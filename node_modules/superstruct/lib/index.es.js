/**
 * A `StructFailure` represents a single specific failure in validation.
 */

/**
 * `StructError` objects are thrown (or returned) when validation fails.
 *
 * Validation logic is design to exit early for maximum performance. The error
 * represents the first error encountered during validation. For more detail,
 * the `error.failures` property is a generator function that can be run to
 * continue validation and receive all the failures in the data.
 */
class StructError extends TypeError {
  constructor(failure, failures) {
    let cached;
    const {
      message,
      ...rest
    } = failure;
    const {
      path
    } = failure;
    const msg = path.length === 0 ? message : "At path: " + path.join('.') + " -- " + message;
    super(msg);
    Object.assign(this, rest);
    this.name = this.constructor.name;

    this.failures = () => {
      var _cached;

      return (_cached = cached) != null ? _cached : cached = [failure, ...failures()];
    };
  }

}

/**
 * Check if a value is an iterator.
 */
function isIterable(x) {
  return isObject(x) && typeof x[Symbol.iterator] === 'function';
}
/**
 * Check if a value is a plain object.
 */


function isObject(x) {
  return typeof x === 'object' && x != null;
}
/**
 * Check if a value is a plain object.
 */

function isPlainObject(x) {
  if (Object.prototype.toString.call(x) !== '[object Object]') {
    return false;
  }

  const prototype = Object.getPrototypeOf(x);
  return prototype === null || prototype === Object.prototype;
}
/**
 * Return a value as a printable string.
 */

function print(value) {
  return typeof value === 'string' ? JSON.stringify(value) : "" + value;
}
/**
 * Shifts (removes and returns) the first value from the `input` iterator.
 * Like `Array.prototype.shift()` but for an `Iterator`.
 */

function shiftIterator(input) {
  const {
    done,
    value
  } = input.next();
  return done ? undefined : value;
}
/**
 * Convert a single validation result to a failure.
 */

function toFailure(result, context, struct, value) {
  if (result === true) {
    return;
  } else if (result === false) {
    result = {};
  } else if (typeof result === 'string') {
    result = {
      message: result
    };
  }

  const {
    path,
    branch
  } = context;
  const {
    type
  } = struct;
  const {
    refinement,
    message = "Expected a value of type `" + type + "`" + (refinement ? " with refinement `" + refinement + "`" : '') + ", but received: `" + print(value) + "`"
  } = result;
  return {
    value,
    type,
    refinement,
    key: path[path.length - 1],
    path,
    branch,
    ...result,
    message
  };
}
/**
 * Convert a validation result to an iterable of failures.
 */

function* toFailures(result, context, struct, value) {
  if (!isIterable(result)) {
    result = [result];
  }

  for (const r of result) {
    const failure = toFailure(r, context, struct, value);

    if (failure) {
      yield failure;
    }
  }
}
/**
 * Check a value against a struct, traversing deeply into nested values, and
 * returning an iterator of failures or success.
 */

function* run(value, struct, options = {}) {
  const {
    path = [],
    branch = [value],
    coerce = false,
    mask = false
  } = options;
  const ctx = {
    path,
    branch
  };

  if (coerce) {
    value = struct.coercer(value, ctx);

    if (mask && struct.type !== 'type' && isObject(struct.schema) && isObject(value) && !Array.isArray(value)) {
      for (const key in value) {
        if (struct.schema[key] === undefined) {
          delete value[key];
        }
      }
    }
  }

  let valid = true;

  for (const failure of struct.validator(value, ctx)) {
    valid = false;
    yield [failure, undefined];
  }

  for (let [k, v, s] of struct.entries(value, ctx)) {
    const ts = run(v, s, {
      path: k === undefined ? path : [...path, k],
      branch: k === undefined ? branch : [...branch, v],
      coerce,
      mask
    });

    for (const t of ts) {
      if (t[0]) {
        valid = false;
        yield [t[0], undefined];
      } else if (coerce) {
        v = t[1];

        if (k === undefined) {
          value = v;
        } else if (value instanceof Map) {
          value.set(k, v);
        } else if (value instanceof Set) {
          value.add(v);
        } else if (isObject(value)) {
          value[k] = v;
        }
      }
    }
  }

  if (valid) {
    for (const failure of struct.refiner(value, ctx)) {
      valid = false;
      yield [failure, undefined];
    }
  }

  if (valid) {
    yield [undefined, value];
  }
}

/**
 * `Struct` objects encapsulate the validation logic for a specific type of
 * values. Once constructed, you use the `assert`, `is` or `validate` helpers to
 * validate unknown input data against the struct.
 */

class Struct {
  constructor(props) {
    const {
      type,
      schema,
      validator,
      refiner,
      coercer = value => value,
      entries = function* () {}
    } = props;
    this.type = type;
    this.schema = schema;
    this.entries = entries;
    this.coercer = coercer;

    if (validator) {
      this.validator = (value, context) => {
        const result = validator(value, context);
        return toFailures(result, context, this, value);
      };
    } else {
      this.validator = () => [];
    }

    if (refiner) {
      this.refiner = (value, context) => {
        const result = refiner(value, context);
        return toFailures(result, context, this, value);
      };
    } else {
      this.refiner = () => [];
    }
  }
  /**
   * Assert that a value passes the struct's validation, throwing if it doesn't.
   */


  assert(value) {
    return assert(value, this);
  }
  /**
   * Create a value with the struct's coercion logic, then validate it.
   */


  create(value) {
    return create(value, this);
  }
  /**
   * Check if a value passes the struct's validation.
   */


  is(value) {
    return is(value, this);
  }
  /**
   * Mask a value, coercing and validating it, but returning only the subset of
   * properties defined by the struct's schema.
   */


  mask(value) {
    return mask(value, this);
  }
  /**
   * Validate a value with the struct's validation logic, returning a tuple
   * representing the result.
   *
   * You may optionally pass `true` for the `withCoercion` argument to coerce
   * the value before attempting to validate it. If you do, the result will
   * contain the coerced result when successful.
   */


  validate(value, options = {}) {
    return validate(value, this, options);
  }

}
/**
 * Assert that a value passes a struct, throwing if it doesn't.
 */

function assert(value, struct) {
  const result = validate(value, struct);

  if (result[0]) {
    throw result[0];
  }
}
/**
 * Create a value with the coercion logic of struct and validate it.
 */

function create(value, struct) {
  const result = validate(value, struct, {
    coerce: true
  });

  if (result[0]) {
    throw result[0];
  } else {
    return result[1];
  }
}
/**
 * Mask a value, returning only the subset of properties defined by a struct.
 */

function mask(value, struct) {
  const result = validate(value, struct, {
    coerce: true,
    mask: true
  });

  if (result[0]) {
    throw result[0];
  } else {
    return result[1];
  }
}
/**
 * Check if a value passes a struct.
 */

function is(value, struct) {
  const result = validate(value, struct);
  return !result[0];
}
/**
 * Validate a value against a struct, returning an error if invalid, or the
 * value (with potential coercion) if valid.
 */

function validate(value, struct, options = {}) {
  const tuples = run(value, struct, options);
  const tuple = shiftIterator(tuples);

  if (tuple[0]) {
    const error = new StructError(tuple[0], function* () {
      for (const t of tuples) {
        if (t[0]) {
          yield t[0];
        }
      }
    });
    return [error, undefined];
  } else {
    const v = tuple[1];
    return [undefined, v];
  }
}

function assign(...Structs) {
  const schemas = Structs.map(s => s.schema);
  const schema = Object.assign({}, ...schemas);
  return object(schema);
}
/**
 * Define a new struct type with a custom validation function.
 */

function define(name, validator) {
  return new Struct({
    type: name,
    schema: null,
    validator
  });
}
/**
 * Create a new struct based on an existing struct, but the value is allowed to
 * be `undefined`. `log` will be called if the value is not `undefined`.
 */

function deprecated(struct, log) {
  return new Struct({ ...struct,
    refiner: (value, ctx) => value === undefined || struct.refiner(value, ctx),

    validator(value, ctx) {
      if (value === undefined) {
        return true;
      } else {
        log(value, ctx);
        return struct.validator(value, ctx);
      }
    }

  });
}
/**
 * Create a struct with dynamic validation logic.
 *
 * The callback will receive the value currently being validated, and must
 * return a struct object to validate it with. This can be useful to model
 * validation logic that changes based on its input.
 */

function dynamic(fn) {
  return new Struct({
    type: 'dynamic',
    schema: null,

    *entries(value, ctx) {
      const struct = fn(value, ctx);
      yield* struct.entries(value, ctx);
    },

    validator(value, ctx) {
      const struct = fn(value, ctx);
      return struct.validator(value, ctx);
    },

    coercer(value, ctx) {
      const struct = fn(value, ctx);
      return struct.coercer(value, ctx);
    }

  });
}
/**
 * Create a struct with lazily evaluated validation logic.
 *
 * The first time validation is run with the struct, the callback will be called
 * and must return a struct object to use. This is useful for cases where you
 * want to have self-referential structs for nested data structures to avoid a
 * circular definition problem.
 */

function lazy(fn) {
  let struct;
  return new Struct({
    type: 'lazy',
    schema: null,

    *entries(value, ctx) {
      var _struct;

      (_struct = struct) != null ? _struct : struct = fn();
      yield* struct.entries(value, ctx);
    },

    validator(value, ctx) {
      var _struct2;

      (_struct2 = struct) != null ? _struct2 : struct = fn();
      return struct.validator(value, ctx);
    },

    coercer(value, ctx) {
      var _struct3;

      (_struct3 = struct) != null ? _struct3 : struct = fn();
      return struct.coercer(value, ctx);
    }

  });
}
/**
 * Create a new struct based on an existing object struct, but excluding
 * specific properties.
 *
 * Like TypeScript's `Omit` utility.
 */

function omit(struct, keys) {
  const {
    schema
  } = struct;
  const subschema = { ...schema
  };

  for (const key of keys) {
    delete subschema[key];
  }

  return object(subschema);
}
/**
 * Create a new struct based on an existing object struct, but with all of its
 * properties allowed to be `undefined`.
 *
 * Like TypeScript's `Partial` utility.
 */

function partial(struct) {
  const schema = struct instanceof Struct ? { ...struct.schema
  } : { ...struct
  };

  for (const key in schema) {
    schema[key] = optional(schema[key]);
  }

  return object(schema);
}
/**
 * Create a new struct based on an existing object struct, but only including
 * specific properties.
 *
 * Like TypeScript's `Pick` utility.
 */

function pick(struct, keys) {
  const {
    schema
  } = struct;
  const subschema = {};

  for (const key of keys) {
    subschema[key] = schema[key];
  }

  return object(subschema);
}
/**
 * Define a new struct type with a custom validation function.
 *
 * @deprecated This function has been renamed to `define`.
 */

function struct(name, validator) {
  console.warn('superstruct@0.11 - The `struct` helper has been renamed to `define`.');
  return define(name, validator);
}

/**
 * Ensure that any value passes validation.
 */

function any() {
  return define('any', () => true);
}
function array(Element) {
  return new Struct({
    type: 'array',
    schema: Element,

    *entries(value) {
      if (Element && Array.isArray(value)) {
        for (const [i, v] of value.entries()) {
          yield [i, v, Element];
        }
      }
    },

    coercer(value) {
      return Array.isArray(value) ? value.slice() : value;
    },

    validator(value) {
      return Array.isArray(value) || "Expected an array value, but received: " + print(value);
    }

  });
}
/**
 * Ensure that a value is a boolean.
 */

function boolean() {
  return define('boolean', value => {
    return typeof value === 'boolean';
  });
}
/**
 * Ensure that a value is a valid `Date`.
 *
 * Note: this also ensures that the value is *not* an invalid `Date` object,
 * which can occur when parsing a date fails but still returns a `Date`.
 */

function date() {
  return define('date', value => {
    return value instanceof Date && !isNaN(value.getTime()) || "Expected a valid `Date` object, but received: " + print(value);
  });
}
function enums(values) {
  const schema = {};
  const description = values.map(v => print(v)).join();

  for (const key of values) {
    schema[key] = key;
  }

  return new Struct({
    type: 'enums',
    schema,

    validator(value) {
      return values.includes(value) || "Expected one of `" + description + "`, but received: " + print(value);
    }

  });
}
/**
 * Ensure that a value is a function.
 */

function func() {
  return define('func', value => {
    return typeof value === 'function' || "Expected a function, but received: " + print(value);
  });
}
/**
 * Ensure that a value is an instance of a specific class.
 */

function instance(Class) {
  return define('instance', value => {
    return value instanceof Class || "Expected a `" + Class.name + "` instance, but received: " + print(value);
  });
}
/**
 * Ensure that a value is an integer.
 */

function integer() {
  return define('integer', value => {
    return typeof value === 'number' && !isNaN(value) && Number.isInteger(value) || "Expected an integer, but received: " + print(value);
  });
}
function intersection(Structs) {
  return new Struct({
    type: 'intersection',
    schema: null,

    *entries(value, ctx) {
      for (const S of Structs) {
        yield* S.entries(value, ctx);
      }
    },

    *validator(value, ctx) {
      for (const S of Structs) {
        yield* S.validator(value, ctx);
      }
    },

    *refiner(value, ctx) {
      for (const S of Structs) {
        yield* S.refiner(value, ctx);
      }
    }

  });
}
function literal(constant) {
  const description = print(constant);
  const t = typeof constant;
  return new Struct({
    type: 'literal',
    schema: t === 'string' || t === 'number' || t === 'boolean' ? constant : null,

    validator(value) {
      return value === constant || "Expected the literal `" + description + "`, but received: " + print(value);
    }

  });
}
function map(Key, Value) {
  return new Struct({
    type: 'map',
    schema: null,

    *entries(value) {
      if (Key && Value && value instanceof Map) {
        for (const [k, v] of value.entries()) {
          yield [k, k, Key];
          yield [k, v, Value];
        }
      }
    },

    coercer(value) {
      return value instanceof Map ? new Map(value) : value;
    },

    validator(value) {
      return value instanceof Map || "Expected a `Map` object, but received: " + print(value);
    }

  });
}
/**
 * Ensure that no value ever passes validation.
 */

function never() {
  return define('never', () => false);
}
/**
 * Augment an existing struct to allow `null` values.
 */

function nullable(struct) {
  return new Struct({ ...struct,
    validator: (value, ctx) => value === null || struct.validator(value, ctx),
    refiner: (value, ctx) => value === null || struct.refiner(value, ctx)
  });
}
/**
 * Ensure that a value is a number.
 */

function number() {
  return define('number', value => {
    return typeof value === 'number' && !isNaN(value) || "Expected a number, but received: " + print(value);
  });
}
function object(schema) {
  const knowns = schema ? Object.keys(schema) : [];
  const Never = never();
  return new Struct({
    type: 'object',
    schema: schema ? schema : null,

    *entries(value) {
      if (schema && isObject(value)) {
        const unknowns = new Set(Object.keys(value));

        for (const key of knowns) {
          unknowns.delete(key);
          yield [key, value[key], schema[key]];
        }

        for (const key of unknowns) {
          yield [key, value[key], Never];
        }
      }
    },

    validator(value) {
      return isObject(value) || "Expected an object, but received: " + print(value);
    },

    coercer(value) {
      return isObject(value) ? { ...value
      } : value;
    }

  });
}
/**
 * Augment a struct to allow `undefined` values.
 */

function optional(struct) {
  return new Struct({ ...struct,
    validator: (value, ctx) => value === undefined || struct.validator(value, ctx),
    refiner: (value, ctx) => value === undefined || struct.refiner(value, ctx)
  });
}
/**
 * Ensure that a value is an object with keys and values of specific types, but
 * without ensuring any specific shape of properties.
 *
 * Like TypeScript's `Record` utility.
 */

function record(Key, Value) {
  return new Struct({
    type: 'record',
    schema: null,

    *entries(value) {
      if (isObject(value)) {
        for (const k in value) {
          const v = value[k];
          yield [k, k, Key];
          yield [k, v, Value];
        }
      }
    },

    validator(value) {
      return isObject(value) || "Expected an object, but received: " + print(value);
    }

  });
}
/**
 * Ensure that a value is a `RegExp`.
 *
 * Note: this does not test the value against the regular expression! For that
 * you need to use the `pattern()` refinement.
 */

function regexp() {
  return define('regexp', value => {
    return value instanceof RegExp;
  });
}
function set(Element) {
  return new Struct({
    type: 'set',
    schema: null,

    *entries(value) {
      if (Element && value instanceof Set) {
        for (const v of value) {
          yield [v, v, Element];
        }
      }
    },

    coercer(value) {
      return value instanceof Set ? new Set(value) : value;
    },

    validator(value) {
      return value instanceof Set || "Expected a `Set` object, but received: " + print(value);
    }

  });
}
/**
 * Ensure that a value is a string.
 */

function string() {
  return define('string', value => {
    return typeof value === 'string' || "Expected a string, but received: " + print(value);
  });
}
function tuple(Elements) {
  const Never = never();
  return new Struct({
    type: 'tuple',
    schema: null,

    *entries(value) {
      if (Array.isArray(value)) {
        const length = Math.max(Elements.length, value.length);

        for (let i = 0; i < length; i++) {
          yield [i, value[i], Elements[i] || Never];
        }
      }
    },

    validator(value) {
      return Array.isArray(value) || "Expected an array, but received: " + print(value);
    }

  });
}
/**
 * Ensure that a value has a set of known properties of specific types.
 *
 * Note: Unrecognized properties are allowed and untouched. This is similar to
 * how TypeScript's structural typing works.
 */

function type(schema) {
  const keys = Object.keys(schema);
  return new Struct({
    type: 'type',
    schema,

    *entries(value) {
      if (isObject(value)) {
        for (const k of keys) {
          yield [k, value[k], schema[k]];
        }
      }
    },

    validator(value) {
      return isObject(value) || "Expected an object, but received: " + print(value);
    }

  });
}
function union(Structs) {
  const description = Structs.map(s => s.type).join(' | ');
  return new Struct({
    type: 'union',
    schema: null,

    validator(value, ctx) {
      const failures = [];

      for (const S of Structs) {
        const [...tuples] = run(value, S, ctx);
        const [first] = tuples;

        if (!first[0]) {
          return [];
        } else {
          for (const [failure] of tuples) {
            if (failure) {
              failures.push(failure);
            }
          }
        }
      }

      return ["Expected the value to satisfy a union of `" + description + "`, but received: " + print(value), ...failures];
    }

  });
}
/**
 * Ensure that any value passes validation, without widening its type to `any`.
 */

function unknown() {
  return define('unknown', () => true);
}

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

function coerce(struct, condition, coercer) {
  return new Struct({ ...struct,
    coercer: (value, ctx) => {
      return is(value, condition) ? struct.coercer(coercer(value, ctx), ctx) : struct.coercer(value, ctx);
    }
  });
}
/**
 * Augment a struct to replace `undefined` values with a default.
 *
 * Note: You must use `create(value, Struct)` on the value to have the coercion
 * take effect! Using simply `assert()` or `is()` will not use coercion.
 */

function defaulted(struct, fallback, options = {}) {
  return coerce(struct, unknown(), x => {
    const f = typeof fallback === 'function' ? fallback() : fallback;

    if (x === undefined) {
      return f;
    }

    if (!options.strict && isPlainObject(x) && isPlainObject(f)) {
      const ret = { ...x
      };
      let changed = false;

      for (const key in f) {
        if (ret[key] === undefined) {
          ret[key] = f[key];
          changed = true;
        }
      }

      if (changed) {
        return ret;
      }
    }

    return x;
  });
}
/**
 * Augment a struct to trim string inputs.
 *
 * Note: You must use `create(value, Struct)` on the value to have the coercion
 * take effect! Using simply `assert()` or `is()` will not use coercion.
 */

function trimmed(struct) {
  return coerce(struct, string(), x => x.trim());
}

/**
 * Ensure that a string, array, map, or set is empty.
 */

function empty(struct) {
  const expected = "Expected an empty " + struct.type;
  return refine(struct, 'empty', value => {
    if (value instanceof Map || value instanceof Set) {
      const {
        size
      } = value;
      return size === 0 || expected + " but received one with a size of `" + size + "`";
    } else {
      const {
        length
      } = value;
      return length === 0 || expected + " but received one with a length of `" + length + "`";
    }
  });
}
/**
 * Ensure that a number or date is below a threshold.
 */

function max(struct, threshold, options = {}) {
  const {
    exclusive
  } = options;
  return refine(struct, 'max', value => {
    return exclusive ? value < threshold : value <= threshold || "Expected a " + struct.type + " greater than " + (exclusive ? '' : 'or equal to ') + threshold + " but received `" + value + "`";
  });
}
/**
 * Ensure that a number or date is above a threshold.
 */

function min(struct, threshold, options = {}) {
  const {
    exclusive
  } = options;
  return refine(struct, 'min', value => {
    return exclusive ? value > threshold : value >= threshold || "Expected a " + struct.type + " greater than " + (exclusive ? '' : 'or equal to ') + threshold + " but received `" + value + "`";
  });
}
/**
 * Ensure that a string matches a regular expression.
 */

function pattern(struct, regexp) {
  return refine(struct, 'pattern', value => {
    return regexp.test(value) || "Expected a " + struct.type + " matching `/" + regexp.source + "/` but received \"" + value + "\"";
  });
}
/**
 * Ensure that a string, array, number, date, map, or set has a size (or length, or time) between `min` and `max`.
 */

function size(struct, min, max = min) {
  const expected = "Expected a " + struct.type;
  const of = min === max ? "of `" + min + "`" : "between `" + min + "` and `" + max + "`";
  return refine(struct, 'size', value => {
    if (typeof value === 'number' || value instanceof Date) {
      return min <= value && value <= max || expected + " " + of + " but received `" + value + "`";
    } else if (value instanceof Map || value instanceof Set) {
      const {
        size
      } = value;
      return min <= size && size <= max || expected + " with a size " + of + " but received one with a size of `" + size + "`";
    } else {
      const {
        length
      } = value;
      return min <= length && length <= max || expected + " with a length " + of + " but received one with a length of `" + length + "`";
    }
  });
}
/**
 * Augment a `Struct` to add an additional refinement to the validation.
 *
 * The refiner function is guaranteed to receive a value of the struct's type,
 * because the struct's existing validation will already have passed. This
 * allows you to layer additional validation on top of existing structs.
 */

function refine(struct, name, refiner) {
  return new Struct({ ...struct,

    *refiner(value, ctx) {
      yield* struct.refiner(value, ctx);
      const result = refiner(value, ctx);
      const failures = toFailures(result, ctx, struct, value);

      for (const failure of failures) {
        yield { ...failure,
          refinement: name
        };
      }
    }

  });
}

export { Struct, StructError, any, array, assert, assign, boolean, coerce, create, date, defaulted, define, deprecated, dynamic, empty, enums, func, instance, integer, intersection, is, lazy, literal, map, mask, max, min, never, nullable, number, object, omit, optional, partial, pattern, pick, record, refine, regexp, set, size, string, struct, trimmed, tuple, type, union, unknown, validate };
//# sourceMappingURL=index.es.js.map
