"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createError = createError;
var errors = new Map([[-32000, "Event not provided"], [-32600, "Invalid Request"], [-32601, "Method not found"], [-32602, "Invalid params"], [-32603, "Internal error"], [-32604, "Params not found"], [-32605, "Method forbidden"], [-32606, "Event forbidden"], [-32700, "Parse error"]]);
/**
 * Creates a JSON-RPC 2.0-compliant error.
 * @param {Number} code - error code
 * @param {String} details - error details
 * @return {Object}
 */

function createError(code, details) {
  var error = {
    code: code,
    message: errors.get(code) || "Internal Server Error"
  };
  if (details) error["data"] = details;
  return error;
}