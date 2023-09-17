/**
 * "Client" wraps "ws" or a browser-implemented "WebSocket" library
 * according to the environment providing JSON RPC 2.0 support on top.
 * @module Client
 */
"use strict";

var _interopRequireDefault = require("@babel/runtime/helpers/interopRequireDefault");

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports["default"] = void 0;

var _regenerator = _interopRequireDefault(require("@babel/runtime/regenerator"));

var _asyncToGenerator2 = _interopRequireDefault(require("@babel/runtime/helpers/asyncToGenerator"));

var _typeof2 = _interopRequireDefault(require("@babel/runtime/helpers/typeof"));

var _classCallCheck2 = _interopRequireDefault(require("@babel/runtime/helpers/classCallCheck"));

var _createClass2 = _interopRequireDefault(require("@babel/runtime/helpers/createClass"));

var _inherits2 = _interopRequireDefault(require("@babel/runtime/helpers/inherits"));

var _possibleConstructorReturn2 = _interopRequireDefault(require("@babel/runtime/helpers/possibleConstructorReturn"));

var _getPrototypeOf2 = _interopRequireDefault(require("@babel/runtime/helpers/getPrototypeOf"));

var _eventemitter = require("eventemitter3");

function _createSuper(Derived) { var hasNativeReflectConstruct = _isNativeReflectConstruct(); return function _createSuperInternal() { var Super = (0, _getPrototypeOf2["default"])(Derived), result; if (hasNativeReflectConstruct) { var NewTarget = (0, _getPrototypeOf2["default"])(this).constructor; result = Reflect.construct(Super, arguments, NewTarget); } else { result = Super.apply(this, arguments); } return (0, _possibleConstructorReturn2["default"])(this, result); }; }

function _isNativeReflectConstruct() { if (typeof Reflect === "undefined" || !Reflect.construct) return false; if (Reflect.construct.sham) return false; if (typeof Proxy === "function") return true; try { Boolean.prototype.valueOf.call(Reflect.construct(Boolean, [], function () {})); return true; } catch (e) { return false; } }

var __rest = void 0 && (void 0).__rest || function (s, e) {
  var t = {};

  for (var p in s) {
    if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0) t[p] = s[p];
  }

  if (s != null && typeof Object.getOwnPropertySymbols === "function") for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
    if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i])) t[p[i]] = s[p[i]];
  }
  return t;
}; // @ts-ignore


var CommonClient = /*#__PURE__*/function (_EventEmitter) {
  (0, _inherits2["default"])(CommonClient, _EventEmitter);

  var _super = _createSuper(CommonClient);

  /**
   * Instantiate a Client class.
   * @constructor
   * @param {webSocketFactory} webSocketFactory - factory method for WebSocket
   * @param {String} address - url to a websocket server
   * @param {Object} options - ws options object with reconnect parameters
   * @param {Function} generate_request_id - custom generation request Id
   * @return {CommonClient}
   */
  function CommonClient(webSocketFactory) {
    var _this;

    var address = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "ws://localhost:8080";

    var _a = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : {};

    var generate_request_id = arguments.length > 3 ? arguments[3] : undefined;
    (0, _classCallCheck2["default"])(this, CommonClient);

    var _a$autoconnect = _a.autoconnect,
        autoconnect = _a$autoconnect === void 0 ? true : _a$autoconnect,
        _a$reconnect = _a.reconnect,
        reconnect = _a$reconnect === void 0 ? true : _a$reconnect,
        _a$reconnect_interval = _a.reconnect_interval,
        reconnect_interval = _a$reconnect_interval === void 0 ? 1000 : _a$reconnect_interval,
        _a$max_reconnects = _a.max_reconnects,
        max_reconnects = _a$max_reconnects === void 0 ? 5 : _a$max_reconnects,
        rest_options = __rest(_a, ["autoconnect", "reconnect", "reconnect_interval", "max_reconnects"]);

    _this = _super.call(this);
    _this.webSocketFactory = webSocketFactory;
    _this.queue = {};
    _this.rpc_id = 0;
    _this.address = address;
    _this.autoconnect = autoconnect;
    _this.ready = false;
    _this.reconnect = reconnect;
    _this.reconnect_timer_id = undefined;
    _this.reconnect_interval = reconnect_interval;
    _this.max_reconnects = max_reconnects;
    _this.rest_options = rest_options;
    _this.current_reconnects = 0;

    _this.generate_request_id = generate_request_id || function () {
      return ++_this.rpc_id;
    };

    if (_this.autoconnect) _this._connect(_this.address, Object.assign({
      autoconnect: _this.autoconnect,
      reconnect: _this.reconnect,
      reconnect_interval: _this.reconnect_interval,
      max_reconnects: _this.max_reconnects
    }, _this.rest_options));
    return _this;
  }
  /**
   * Connects to a defined server if not connected already.
   * @method
   * @return {Undefined}
   */


  (0, _createClass2["default"])(CommonClient, [{
    key: "connect",
    value: function connect() {
      if (this.socket) return;

      this._connect(this.address, Object.assign({
        autoconnect: this.autoconnect,
        reconnect: this.reconnect,
        reconnect_interval: this.reconnect_interval,
        max_reconnects: this.max_reconnects
      }, this.rest_options));
    }
    /**
     * Calls a registered RPC method on server.
     * @method
     * @param {String} method - RPC method name
     * @param {Object|Array} params - optional method parameters
     * @param {Number} timeout - RPC reply timeout value
     * @param {Object} ws_opts - options passed to ws
     * @return {Promise}
     */

  }, {
    key: "call",
    value: function call(method, params, timeout, ws_opts) {
      var _this2 = this;

      if (!ws_opts && "object" === (0, _typeof2["default"])(timeout)) {
        ws_opts = timeout;
        timeout = null;
      }

      return new Promise(function (resolve, reject) {
        if (!_this2.ready) return reject(new Error("socket not ready"));

        var rpc_id = _this2.generate_request_id(method, params);

        var message = {
          jsonrpc: "2.0",
          method: method,
          params: params || null,
          id: rpc_id
        };

        _this2.socket.send(JSON.stringify(message), ws_opts, function (error) {
          if (error) return reject(error);
          _this2.queue[rpc_id] = {
            promise: [resolve, reject]
          };

          if (timeout) {
            _this2.queue[rpc_id].timeout = setTimeout(function () {
              delete _this2.queue[rpc_id];
              reject(new Error("reply timeout"));
            }, timeout);
          }
        });
      });
    }
    /**
     * Logins with the other side of the connection.
     * @method
     * @param {Object} params - Login credentials object
     * @return {Promise}
     */

  }, {
    key: "login",
    value: function () {
      var _login = (0, _asyncToGenerator2["default"])( /*#__PURE__*/_regenerator["default"].mark(function _callee(params) {
        var resp;
        return _regenerator["default"].wrap(function _callee$(_context) {
          while (1) {
            switch (_context.prev = _context.next) {
              case 0:
                _context.next = 2;
                return this.call("rpc.login", params);

              case 2:
                resp = _context.sent;

                if (resp) {
                  _context.next = 5;
                  break;
                }

                throw new Error("authentication failed");

              case 5:
                return _context.abrupt("return", resp);

              case 6:
              case "end":
                return _context.stop();
            }
          }
        }, _callee, this);
      }));

      function login(_x) {
        return _login.apply(this, arguments);
      }

      return login;
    }()
    /**
     * Fetches a list of client's methods registered on server.
     * @method
     * @return {Array}
     */

  }, {
    key: "listMethods",
    value: function () {
      var _listMethods = (0, _asyncToGenerator2["default"])( /*#__PURE__*/_regenerator["default"].mark(function _callee2() {
        return _regenerator["default"].wrap(function _callee2$(_context2) {
          while (1) {
            switch (_context2.prev = _context2.next) {
              case 0:
                _context2.next = 2;
                return this.call("__listMethods");

              case 2:
                return _context2.abrupt("return", _context2.sent);

              case 3:
              case "end":
                return _context2.stop();
            }
          }
        }, _callee2, this);
      }));

      function listMethods() {
        return _listMethods.apply(this, arguments);
      }

      return listMethods;
    }()
    /**
     * Sends a JSON-RPC 2.0 notification to server.
     * @method
     * @param {String} method - RPC method name
     * @param {Object} params - optional method parameters
     * @return {Promise}
     */

  }, {
    key: "notify",
    value: function notify(method, params) {
      var _this3 = this;

      return new Promise(function (resolve, reject) {
        if (!_this3.ready) return reject(new Error("socket not ready"));
        var message = {
          jsonrpc: "2.0",
          method: method,
          params: params || null
        };

        _this3.socket.send(JSON.stringify(message), function (error) {
          if (error) return reject(error);
          resolve();
        });
      });
    }
    /**
     * Subscribes for a defined event.
     * @method
     * @param {String|Array} event - event name
     * @return {Undefined}
     * @throws {Error}
     */

  }, {
    key: "subscribe",
    value: function () {
      var _subscribe = (0, _asyncToGenerator2["default"])( /*#__PURE__*/_regenerator["default"].mark(function _callee3(event) {
        var result;
        return _regenerator["default"].wrap(function _callee3$(_context3) {
          while (1) {
            switch (_context3.prev = _context3.next) {
              case 0:
                if (typeof event === "string") event = [event];
                _context3.next = 3;
                return this.call("rpc.on", event);

              case 3:
                result = _context3.sent;

                if (!(typeof event === "string" && result[event] !== "ok")) {
                  _context3.next = 6;
                  break;
                }

                throw new Error("Failed subscribing to an event '" + event + "' with: " + result[event]);

              case 6:
                return _context3.abrupt("return", result);

              case 7:
              case "end":
                return _context3.stop();
            }
          }
        }, _callee3, this);
      }));

      function subscribe(_x2) {
        return _subscribe.apply(this, arguments);
      }

      return subscribe;
    }()
    /**
     * Unsubscribes from a defined event.
     * @method
     * @param {String|Array} event - event name
     * @return {Undefined}
     * @throws {Error}
     */

  }, {
    key: "unsubscribe",
    value: function () {
      var _unsubscribe = (0, _asyncToGenerator2["default"])( /*#__PURE__*/_regenerator["default"].mark(function _callee4(event) {
        var result;
        return _regenerator["default"].wrap(function _callee4$(_context4) {
          while (1) {
            switch (_context4.prev = _context4.next) {
              case 0:
                if (typeof event === "string") event = [event];
                _context4.next = 3;
                return this.call("rpc.off", event);

              case 3:
                result = _context4.sent;

                if (!(typeof event === "string" && result[event] !== "ok")) {
                  _context4.next = 6;
                  break;
                }

                throw new Error("Failed unsubscribing from an event with: " + result);

              case 6:
                return _context4.abrupt("return", result);

              case 7:
              case "end":
                return _context4.stop();
            }
          }
        }, _callee4, this);
      }));

      function unsubscribe(_x3) {
        return _unsubscribe.apply(this, arguments);
      }

      return unsubscribe;
    }()
    /**
     * Closes a WebSocket connection gracefully.
     * @method
     * @param {Number} code - socket close code
     * @param {String} data - optional data to be sent before closing
     * @return {Undefined}
     */

  }, {
    key: "close",
    value: function close(code, data) {
      this.socket.close(code || 1000, data);
    }
    /**
     * Connection/Message handler.
     * @method
     * @private
     * @param {String} address - WebSocket API address
     * @param {Object} options - ws options object
     * @return {Undefined}
     */

  }, {
    key: "_connect",
    value: function _connect(address, options) {
      var _this4 = this;

      clearTimeout(this.reconnect_timer_id);
      this.socket = this.webSocketFactory(address, options);
      this.socket.addEventListener("open", function () {
        _this4.ready = true;

        _this4.emit("open");

        _this4.current_reconnects = 0;
      });
      this.socket.addEventListener("message", function (_ref) {
        var message = _ref.data;
        if (message instanceof ArrayBuffer) message = Buffer.from(message).toString();

        try {
          message = JSON.parse(message);
        } catch (error) {
          return;
        } // check if any listeners are attached and forward event


        if (message.notification && _this4.listeners(message.notification).length) {
          if (!Object.keys(message.params).length) return _this4.emit(message.notification);
          var args = [message.notification];
          if (message.params.constructor === Object) args.push(message.params);else // using for-loop instead of unshift/spread because performance is better
            for (var i = 0; i < message.params.length; i++) {
              args.push(message.params[i]);
            } // run as microtask so that pending queue messages are resolved first
          // eslint-disable-next-line prefer-spread

          return Promise.resolve().then(function () {
            _this4.emit.apply(_this4, args);
          });
        }

        if (!_this4.queue[message.id]) {
          // general JSON RPC 2.0 events
          if (message.method && message.params) {
            // run as microtask so that pending queue messages are resolved first
            return Promise.resolve().then(function () {
              _this4.emit(message.method, message.params);
            });
          }

          return;
        } // reject early since server's response is invalid


        if ("error" in message === "result" in message) _this4.queue[message.id].promise[1](new Error("Server response malformed. Response must include either \"result\"" + " or \"error\", but not both."));
        if (_this4.queue[message.id].timeout) clearTimeout(_this4.queue[message.id].timeout);
        if (message.error) _this4.queue[message.id].promise[1](message.error);else _this4.queue[message.id].promise[0](message.result);
        delete _this4.queue[message.id];
      });
      this.socket.addEventListener("error", function (error) {
        return _this4.emit("error", error);
      });
      this.socket.addEventListener("close", function (_ref2) {
        var code = _ref2.code,
            reason = _ref2.reason;
        if (_this4.ready) // Delay close event until internal state is updated
          setTimeout(function () {
            return _this4.emit("close", code, reason);
          }, 0);
        _this4.ready = false;
        _this4.socket = undefined;
        if (code === 1000) return;
        _this4.current_reconnects++;
        if (_this4.reconnect && (_this4.max_reconnects > _this4.current_reconnects || _this4.max_reconnects === 0)) _this4.reconnect_timer_id = setTimeout(function () {
          return _this4._connect(address, options);
        }, _this4.reconnect_interval);
      });
    }
  }]);
  return CommonClient;
}(_eventemitter.EventEmitter);

exports["default"] = CommonClient;