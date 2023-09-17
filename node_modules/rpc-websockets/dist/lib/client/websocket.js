/* A wrapper for the "qaap/uws-bindings" library. */
"use strict";

var _interopRequireDefault = require("@babel/runtime/helpers/interopRequireDefault");

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports["default"] = _default;

var _ws = _interopRequireDefault(require("ws"));

/**
 * factory method for common WebSocket instance
 * @method
 * @param {String} address - url to a websocket server
 * @param {(Object)} options - websocket options
 * @return {Undefined}
 */
function _default(address, options) {
  return new _ws["default"](address, options);
}