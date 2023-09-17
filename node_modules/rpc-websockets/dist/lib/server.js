/**
 * "Server" wraps the "ws" library providing JSON RPC 2.0 support on top.
 * @module Server
 */
"use strict"; // @ts-ignore

var _interopRequireDefault = require("@babel/runtime/helpers/interopRequireDefault");

var _typeof3 = require("@babel/runtime/helpers/typeof");

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports["default"] = void 0;

var _regenerator = _interopRequireDefault(require("@babel/runtime/regenerator"));

var _typeof2 = _interopRequireDefault(require("@babel/runtime/helpers/typeof"));

var _asyncToGenerator2 = _interopRequireDefault(require("@babel/runtime/helpers/asyncToGenerator"));

var _defineProperty2 = _interopRequireDefault(require("@babel/runtime/helpers/defineProperty"));

var _toConsumableArray2 = _interopRequireDefault(require("@babel/runtime/helpers/toConsumableArray"));

var _classCallCheck2 = _interopRequireDefault(require("@babel/runtime/helpers/classCallCheck"));

var _createClass2 = _interopRequireDefault(require("@babel/runtime/helpers/createClass"));

var _inherits2 = _interopRequireDefault(require("@babel/runtime/helpers/inherits"));

var _possibleConstructorReturn2 = _interopRequireDefault(require("@babel/runtime/helpers/possibleConstructorReturn"));

var _getPrototypeOf2 = _interopRequireDefault(require("@babel/runtime/helpers/getPrototypeOf"));

var _eventemitter = require("eventemitter3");

var _ws = require("ws");

var _uuid = require("uuid");

var _url = _interopRequireDefault(require("url"));

var utils = _interopRequireWildcard(require("./utils"));

function _getRequireWildcardCache(nodeInterop) { if (typeof WeakMap !== "function") return null; var cacheBabelInterop = new WeakMap(); var cacheNodeInterop = new WeakMap(); return (_getRequireWildcardCache = function _getRequireWildcardCache(nodeInterop) { return nodeInterop ? cacheNodeInterop : cacheBabelInterop; })(nodeInterop); }

function _interopRequireWildcard(obj, nodeInterop) { if (!nodeInterop && obj && obj.__esModule) { return obj; } if (obj === null || _typeof3(obj) !== "object" && typeof obj !== "function") { return { "default": obj }; } var cache = _getRequireWildcardCache(nodeInterop); if (cache && cache.has(obj)) { return cache.get(obj); } var newObj = {}; var hasPropertyDescriptor = Object.defineProperty && Object.getOwnPropertyDescriptor; for (var key in obj) { if (key !== "default" && Object.prototype.hasOwnProperty.call(obj, key)) { var desc = hasPropertyDescriptor ? Object.getOwnPropertyDescriptor(obj, key) : null; if (desc && (desc.get || desc.set)) { Object.defineProperty(newObj, key, desc); } else { newObj[key] = obj[key]; } } } newObj["default"] = obj; if (cache) { cache.set(obj, newObj); } return newObj; }

function _createForOfIteratorHelper(o, allowArrayLike) { var it = typeof Symbol !== "undefined" && o[Symbol.iterator] || o["@@iterator"]; if (!it) { if (Array.isArray(o) || (it = _unsupportedIterableToArray(o)) || allowArrayLike && o && typeof o.length === "number") { if (it) o = it; var i = 0; var F = function F() {}; return { s: F, n: function n() { if (i >= o.length) return { done: true }; return { done: false, value: o[i++] }; }, e: function e(_e) { throw _e; }, f: F }; } throw new TypeError("Invalid attempt to iterate non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method."); } var normalCompletion = true, didErr = false, err; return { s: function s() { it = it.call(o); }, n: function n() { var step = it.next(); normalCompletion = step.done; return step; }, e: function e(_e2) { didErr = true; err = _e2; }, f: function f() { try { if (!normalCompletion && it["return"] != null) it["return"](); } finally { if (didErr) throw err; } } }; }

function _unsupportedIterableToArray(o, minLen) { if (!o) return; if (typeof o === "string") return _arrayLikeToArray(o, minLen); var n = Object.prototype.toString.call(o).slice(8, -1); if (n === "Object" && o.constructor) n = o.constructor.name; if (n === "Map" || n === "Set") return Array.from(o); if (n === "Arguments" || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(n)) return _arrayLikeToArray(o, minLen); }

function _arrayLikeToArray(arr, len) { if (len == null || len > arr.length) len = arr.length; for (var i = 0, arr2 = new Array(len); i < len; i++) { arr2[i] = arr[i]; } return arr2; }

function _createSuper(Derived) { var hasNativeReflectConstruct = _isNativeReflectConstruct(); return function _createSuperInternal() { var Super = (0, _getPrototypeOf2["default"])(Derived), result; if (hasNativeReflectConstruct) { var NewTarget = (0, _getPrototypeOf2["default"])(this).constructor; result = Reflect.construct(Super, arguments, NewTarget); } else { result = Super.apply(this, arguments); } return (0, _possibleConstructorReturn2["default"])(this, result); }; }

function _isNativeReflectConstruct() { if (typeof Reflect === "undefined" || !Reflect.construct) return false; if (Reflect.construct.sham) return false; if (typeof Proxy === "function") return true; try { Boolean.prototype.valueOf.call(Reflect.construct(Boolean, [], function () {})); return true; } catch (e) { return false; } }

var Server = /*#__PURE__*/function (_EventEmitter) {
  (0, _inherits2["default"])(Server, _EventEmitter);

  var _super = _createSuper(Server);

  /**
   * Instantiate a Server class.
   * @constructor
   * @param {Object} options - ws constructor's parameters with rpc
   * @return {Server} - returns a new Server instance
   */
  function Server(options) {
    var _this;

    (0, _classCallCheck2["default"])(this, Server);
    _this = _super.call(this);
    /**
     * Stores all connected sockets with a universally unique identifier
     * in the appropriate namespace.
     * Stores all rpc methods to specific namespaces. "/" by default.
     * Stores all events as keys and subscribed users in array as value
     * @private
     * @name namespaces
     * @param {Object} namespaces.rpc_methods
     * @param {Map} namespaces.clients
     * @param {Object} namespaces.events
     */

    _this.namespaces = {};
    _this.wss = new _ws.Server(options);

    _this.wss.on("listening", function () {
      return _this.emit("listening");
    });

    _this.wss.on("connection", function (socket, request) {
      var u = _url["default"].parse(request.url, true);

      var ns = u.pathname;
      if (u.query.socket_id) socket._id = u.query.socket_id;else socket._id = (0, _uuid.v1)(); // unauthenticated by default

      socket["_authenticated"] = false; // propagate socket errors

      socket.on("error", function (error) {
        return _this.emit("socket-error", socket, error);
      }); // cleanup after the socket gets disconnected

      socket.on("close", function () {
        _this.namespaces[ns].clients["delete"](socket._id);

        for (var _i = 0, _Object$keys = Object.keys(_this.namespaces[ns].events); _i < _Object$keys.length; _i++) {
          var event = _Object$keys[_i];

          var index = _this.namespaces[ns].events[event].sockets.indexOf(socket._id);

          if (index >= 0) _this.namespaces[ns].events[event].sockets.splice(index, 1);
        }

        _this.emit("disconnection", socket);
      });
      if (!_this.namespaces[ns]) _this._generateNamespace(ns); // store socket and method

      _this.namespaces[ns].clients.set(socket._id, socket);

      _this.emit("connection", socket, request);

      return _this._handleRPC(socket, ns);
    });

    _this.wss.on("error", function (error) {
      return _this.emit("error", error);
    });

    return _this;
  }
  /**
   * Registers an RPC method.
   * @method
   * @param {String} name - method name
   * @param {Function} fn - a callee function
   * @param {String} ns - namespace identifier
   * @throws {TypeError}
   * @return {Object} - returns an IMethod object
   */


  (0, _createClass2["default"])(Server, [{
    key: "register",
    value: function register(name, fn) {
      var _this2 = this;

      var ns = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : "/";
      if (!this.namespaces[ns]) this._generateNamespace(ns);
      this.namespaces[ns].rpc_methods[name] = {
        fn: fn,
        "protected": false
      };
      return {
        "protected": function _protected() {
          return _this2._makeProtectedMethod(name, ns);
        },
        "public": function _public() {
          return _this2._makePublicMethod(name, ns);
        }
      };
    }
    /**
     * Sets an auth method.
     * @method
     * @param {Function} fn - an arbitrary auth method
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Undefined}
     */

  }, {
    key: "setAuth",
    value: function setAuth(fn) {
      var ns = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "/";
      this.register("rpc.login", fn, ns);
    }
    /**
     * Marks an RPC method as protected.
     * @method
     * @param {String} name - method name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */

  }, {
    key: "_makeProtectedMethod",
    value: function _makeProtectedMethod(name) {
      var ns = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "/";
      this.namespaces[ns].rpc_methods[name]["protected"] = true;
    }
    /**
     * Marks an RPC method as public.
     * @method
     * @param {String} name - method name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */

  }, {
    key: "_makePublicMethod",
    value: function _makePublicMethod(name) {
      var ns = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "/";
      this.namespaces[ns].rpc_methods[name]["protected"] = false;
    }
    /**
     * Marks an event as protected.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */

  }, {
    key: "_makeProtectedEvent",
    value: function _makeProtectedEvent(name) {
      var ns = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "/";
      this.namespaces[ns].events[name]["protected"] = true;
    }
    /**
     * Marks an event as public.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */

  }, {
    key: "_makePublicEvent",
    value: function _makePublicEvent(name) {
      var ns = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "/";
      this.namespaces[ns].events[name]["protected"] = false;
    }
    /**
     * Removes a namespace and closes all connections
     * @method
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Undefined}
     */

  }, {
    key: "closeNamespace",
    value: function closeNamespace(ns) {
      var namespace = this.namespaces[ns];

      if (namespace) {
        delete namespace.rpc_methods;
        delete namespace.events;

        var _iterator = _createForOfIteratorHelper(namespace.clients.values()),
            _step;

        try {
          for (_iterator.s(); !(_step = _iterator.n()).done;) {
            var socket = _step.value;
            socket.close();
          }
        } catch (err) {
          _iterator.e(err);
        } finally {
          _iterator.f();
        }

        delete this.namespaces[ns];
      }
    }
    /**
     * Creates a new event that can be emitted to clients.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Object} - returns an IEvent object
     */

  }, {
    key: "event",
    value: function event(name) {
      var _this3 = this;

      var ns = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "/";
      if (!this.namespaces[ns]) this._generateNamespace(ns);else {
        var index = this.namespaces[ns].events[name];
        if (index !== undefined) throw new Error("Already registered event ".concat(ns).concat(name));
      }
      this.namespaces[ns].events[name] = {
        sockets: [],
        "protected": false
      }; // forward emitted event to subscribers

      this.on(name, function () {
        for (var _len = arguments.length, params = new Array(_len), _key = 0; _key < _len; _key++) {
          params[_key] = arguments[_key];
        }

        // flatten an object if no spreading is wanted
        if (params.length === 1 && params[0] instanceof Object) params = params[0];

        var _iterator2 = _createForOfIteratorHelper(_this3.namespaces[ns].events[name].sockets),
            _step2;

        try {
          for (_iterator2.s(); !(_step2 = _iterator2.n()).done;) {
            var socket_id = _step2.value;

            var socket = _this3.namespaces[ns].clients.get(socket_id);

            if (!socket) continue;
            socket.send(JSON.stringify({
              notification: name,
              params: params || null
            }));
          }
        } catch (err) {
          _iterator2.e(err);
        } finally {
          _iterator2.f();
        }
      });
      return {
        "protected": function _protected() {
          return _this3._makeProtectedEvent(name, ns);
        },
        "public": function _public() {
          return _this3._makePublicEvent(name, ns);
        }
      };
    }
    /**
     * Returns a requested namespace object
     * @method
     * @param {String} name - namespace identifier
     * @throws {TypeError}
     * @return {Object} - namespace object
     */

  }, {
    key: "of",
    value: function of(name) {
      if (!this.namespaces[name]) this._generateNamespace(name);
      var self = this;
      return {
        // self.register convenience method
        register: function register(fn_name, fn) {
          if (arguments.length !== 2) throw new Error("must provide exactly two arguments");
          if (typeof fn_name !== "string") throw new Error("name must be a string");
          if (typeof fn !== "function") throw new Error("handler must be a function");
          return self.register(fn_name, fn, name);
        },
        // self.event convenience method
        event: function event(ev_name) {
          if (arguments.length !== 1) throw new Error("must provide exactly one argument");
          if (typeof ev_name !== "string") throw new Error("name must be a string");
          return self.event(ev_name, name);
        },

        // self.eventList convenience method
        get eventList() {
          return Object.keys(self.namespaces[name].events);
        },

        /**
         * Emits a specified event to this namespace.
         * @inner
         * @method
         * @param {String} event - event name
         * @param {Array} params - event parameters
         * @return {Undefined}
         */
        emit: function emit(event) {
          var socket_ids = (0, _toConsumableArray2["default"])(self.namespaces[name].clients.keys());

          for (var _len2 = arguments.length, params = new Array(_len2 > 1 ? _len2 - 1 : 0), _key2 = 1; _key2 < _len2; _key2++) {
            params[_key2 - 1] = arguments[_key2];
          }

          for (var i = 0, id; id = socket_ids[i]; ++i) {
            self.namespaces[name].clients.get(id).send(JSON.stringify({
              notification: event,
              params: params || []
            }));
          }
        },

        /**
         * Returns a name of this namespace.
         * @inner
         * @method
         * @kind constant
         * @return {String}
         */
        get name() {
          return name;
        },

        /**
         * Returns a hash of websocket objects connected to this namespace.
         * @inner
         * @method
         * @return {Object}
         */
        connected: function connected() {
          var socket_ids = (0, _toConsumableArray2["default"])(self.namespaces[name].clients.keys());
          return socket_ids.reduce(function (acc, curr) {
            return Object.assign(Object.assign({}, acc), (0, _defineProperty2["default"])({}, curr, self.namespaces[name].clients.get(curr)));
          }, {});
        },

        /**
         * Returns a list of client unique identifiers connected to this namespace.
         * @inner
         * @method
         * @return {Array}
         */
        clients: function clients() {
          return self.namespaces[name];
        }
      };
    }
    /**
     * Lists all created events in a given namespace. Defaults to "/".
     * @method
     * @param {String} ns - namespaces identifier
     * @readonly
     * @return {Array} - returns a list of created events
     */

  }, {
    key: "eventList",
    value: function eventList() {
      var ns = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : "/";
      if (!this.namespaces[ns]) return [];
      return Object.keys(this.namespaces[ns].events);
    }
    /**
     * Creates a JSON-RPC 2.0 compliant error
     * @method
     * @param {Number} code - indicates the error type that occurred
     * @param {String} message - provides a short description of the error
     * @param {String|Object} data - details containing additional information about the error
     * @return {Object}
     */

  }, {
    key: "createError",
    value: function createError(code, message, data) {
      return {
        code: code,
        message: message,
        data: data || null
      };
    }
    /**
     * Closes the server and terminates all clients.
     * @method
     * @return {Promise}
     */

  }, {
    key: "close",
    value: function close() {
      var _this4 = this;

      return new Promise(function (resolve, reject) {
        try {
          _this4.wss.close();

          _this4.emit("close");

          resolve();
        } catch (error) {
          reject(error);
        }
      });
    }
    /**
     * Handles all WebSocket JSON RPC 2.0 requests.
     * @private
     * @param {Object} socket - ws socket instance
     * @param {String} ns - namespaces identifier
     * @return {Undefined}
     */

  }, {
    key: "_handleRPC",
    value: function _handleRPC(socket) {
      var _this5 = this;

      var ns = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : "/";
      socket.on("message", /*#__PURE__*/function () {
        var _ref = (0, _asyncToGenerator2["default"])( /*#__PURE__*/_regenerator["default"].mark(function _callee(data) {
          var msg_options, parsedData, responses, _iterator3, _step3, message, _response, response;

          return _regenerator["default"].wrap(function _callee$(_context) {
            while (1) {
              switch (_context.prev = _context.next) {
                case 0:
                  msg_options = {};

                  if (data instanceof ArrayBuffer) {
                    msg_options.binary = true;
                    data = Buffer.from(data).toString();
                  }

                  if (!(socket.readyState !== 1)) {
                    _context.next = 4;
                    break;
                  }

                  return _context.abrupt("return");

                case 4:
                  _context.prev = 4;
                  parsedData = JSON.parse(data);
                  _context.next = 11;
                  break;

                case 8:
                  _context.prev = 8;
                  _context.t0 = _context["catch"](4);
                  return _context.abrupt("return", socket.send(JSON.stringify({
                    jsonrpc: "2.0",
                    error: utils.createError(-32700, _context.t0.toString()),
                    id: null
                  }), msg_options));

                case 11:
                  if (!Array.isArray(parsedData)) {
                    _context.next = 39;
                    break;
                  }

                  if (parsedData.length) {
                    _context.next = 14;
                    break;
                  }

                  return _context.abrupt("return", socket.send(JSON.stringify({
                    jsonrpc: "2.0",
                    error: utils.createError(-32600, "Invalid array"),
                    id: null
                  }), msg_options));

                case 14:
                  responses = [];
                  _iterator3 = _createForOfIteratorHelper(parsedData);
                  _context.prev = 16;

                  _iterator3.s();

                case 18:
                  if ((_step3 = _iterator3.n()).done) {
                    _context.next = 28;
                    break;
                  }

                  message = _step3.value;
                  _context.next = 22;
                  return _this5._runMethod(message, socket._id, ns);

                case 22:
                  _response = _context.sent;

                  if (_response) {
                    _context.next = 25;
                    break;
                  }

                  return _context.abrupt("continue", 26);

                case 25:
                  responses.push(_response);

                case 26:
                  _context.next = 18;
                  break;

                case 28:
                  _context.next = 33;
                  break;

                case 30:
                  _context.prev = 30;
                  _context.t1 = _context["catch"](16);

                  _iterator3.e(_context.t1);

                case 33:
                  _context.prev = 33;

                  _iterator3.f();

                  return _context.finish(33);

                case 36:
                  if (responses.length) {
                    _context.next = 38;
                    break;
                  }

                  return _context.abrupt("return");

                case 38:
                  return _context.abrupt("return", socket.send(JSON.stringify(responses), msg_options));

                case 39:
                  _context.next = 41;
                  return _this5._runMethod(parsedData, socket._id, ns);

                case 41:
                  response = _context.sent;

                  if (response) {
                    _context.next = 44;
                    break;
                  }

                  return _context.abrupt("return");

                case 44:
                  return _context.abrupt("return", socket.send(JSON.stringify(response), msg_options));

                case 45:
                case "end":
                  return _context.stop();
              }
            }
          }, _callee, null, [[4, 8], [16, 30, 33, 36]]);
        }));

        return function (_x) {
          return _ref.apply(this, arguments);
        };
      }());
    }
    /**
     * Runs a defined RPC method.
     * @private
     * @param {Object} message - a message received
     * @param {Object} socket_id - user's socket id
     * @param {String} ns - namespaces identifier
     * @return {Object|undefined}
     */

  }, {
    key: "_runMethod",
    value: function () {
      var _runMethod2 = (0, _asyncToGenerator2["default"])( /*#__PURE__*/_regenerator["default"].mark(function _callee2(message, socket_id) {
        var ns,
            results,
            event_names,
            _iterator4,
            _step4,
            name,
            index,
            namespace,
            socket_index,
            _results,
            _iterator5,
            _step5,
            _name,
            _index,
            response,
            s,
            _args2 = arguments;

        return _regenerator["default"].wrap(function _callee2$(_context2) {
          while (1) {
            switch (_context2.prev = _context2.next) {
              case 0:
                ns = _args2.length > 2 && _args2[2] !== undefined ? _args2[2] : "/";

                if (!((0, _typeof2["default"])(message) !== "object" || message === null)) {
                  _context2.next = 3;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32600),
                  id: null
                });

              case 3:
                if (!(message.jsonrpc !== "2.0")) {
                  _context2.next = 5;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32600, "Invalid JSON RPC version"),
                  id: message.id || null
                });

              case 5:
                if (message.method) {
                  _context2.next = 7;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32602, "Method not specified"),
                  id: message.id || null
                });

              case 7:
                if (!(typeof message.method !== "string")) {
                  _context2.next = 9;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32600, "Invalid method name"),
                  id: message.id || null
                });

              case 9:
                if (!(message.params && typeof message.params === "string")) {
                  _context2.next = 11;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32600),
                  id: message.id || null
                });

              case 11:
                if (!(message.method === "rpc.on")) {
                  _context2.next = 47;
                  break;
                }

                if (message.params) {
                  _context2.next = 14;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32000),
                  id: message.id || null
                });

              case 14:
                results = {};
                event_names = Object.keys(this.namespaces[ns].events);
                _iterator4 = _createForOfIteratorHelper(message.params);
                _context2.prev = 17;

                _iterator4.s();

              case 19:
                if ((_step4 = _iterator4.n()).done) {
                  _context2.next = 36;
                  break;
                }

                name = _step4.value;
                index = event_names.indexOf(name);
                namespace = this.namespaces[ns];

                if (!(index === -1)) {
                  _context2.next = 26;
                  break;
                }

                results[name] = "provided event invalid";
                return _context2.abrupt("continue", 34);

              case 26:
                if (!(namespace.events[event_names[index]]["protected"] === true && namespace.clients.get(socket_id)["_authenticated"] === false)) {
                  _context2.next = 28;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32606),
                  id: message.id || null
                });

              case 28:
                socket_index = namespace.events[event_names[index]].sockets.indexOf(socket_id);

                if (!(socket_index >= 0)) {
                  _context2.next = 32;
                  break;
                }

                results[name] = "socket has already been subscribed to event";
                return _context2.abrupt("continue", 34);

              case 32:
                namespace.events[event_names[index]].sockets.push(socket_id);
                results[name] = "ok";

              case 34:
                _context2.next = 19;
                break;

              case 36:
                _context2.next = 41;
                break;

              case 38:
                _context2.prev = 38;
                _context2.t0 = _context2["catch"](17);

                _iterator4.e(_context2.t0);

              case 41:
                _context2.prev = 41;

                _iterator4.f();

                return _context2.finish(41);

              case 44:
                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  result: results,
                  id: message.id || null
                });

              case 47:
                if (!(message.method === "rpc.off")) {
                  _context2.next = 78;
                  break;
                }

                if (message.params) {
                  _context2.next = 50;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32000),
                  id: message.id || null
                });

              case 50:
                _results = {};
                _iterator5 = _createForOfIteratorHelper(message.params);
                _context2.prev = 52;

                _iterator5.s();

              case 54:
                if ((_step5 = _iterator5.n()).done) {
                  _context2.next = 67;
                  break;
                }

                _name = _step5.value;

                if (this.namespaces[ns].events[_name]) {
                  _context2.next = 59;
                  break;
                }

                _results[_name] = "provided event invalid";
                return _context2.abrupt("continue", 65);

              case 59:
                _index = this.namespaces[ns].events[_name].sockets.indexOf(socket_id);

                if (!(_index === -1)) {
                  _context2.next = 63;
                  break;
                }

                _results[_name] = "not subscribed";
                return _context2.abrupt("continue", 65);

              case 63:
                this.namespaces[ns].events[_name].sockets.splice(_index, 1);

                _results[_name] = "ok";

              case 65:
                _context2.next = 54;
                break;

              case 67:
                _context2.next = 72;
                break;

              case 69:
                _context2.prev = 69;
                _context2.t1 = _context2["catch"](52);

                _iterator5.e(_context2.t1);

              case 72:
                _context2.prev = 72;

                _iterator5.f();

                return _context2.finish(72);

              case 75:
                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  result: _results,
                  id: message.id || null
                });

              case 78:
                if (!(message.method === "rpc.login")) {
                  _context2.next = 81;
                  break;
                }

                if (message.params) {
                  _context2.next = 81;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32604),
                  id: message.id || null
                });

              case 81:
                if (this.namespaces[ns].rpc_methods[message.method]) {
                  _context2.next = 83;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32601),
                  id: message.id || null
                });

              case 83:
                response = null; // reject request if method is protected and if client is not authenticated

                if (!(this.namespaces[ns].rpc_methods[message.method]["protected"] === true && this.namespaces[ns].clients.get(socket_id)["_authenticated"] === false)) {
                  _context2.next = 86;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: utils.createError(-32605),
                  id: message.id || null
                });

              case 86:
                _context2.prev = 86;
                _context2.next = 89;
                return this.namespaces[ns].rpc_methods[message.method].fn(message.params, socket_id);

              case 89:
                response = _context2.sent;
                _context2.next = 99;
                break;

              case 92:
                _context2.prev = 92;
                _context2.t2 = _context2["catch"](86);

                if (message.id) {
                  _context2.next = 96;
                  break;
                }

                return _context2.abrupt("return");

              case 96:
                if (!(_context2.t2 instanceof Error)) {
                  _context2.next = 98;
                  break;
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: {
                    code: -32000,
                    message: _context2.t2.name,
                    data: _context2.t2.message
                  },
                  id: message.id
                });

              case 98:
                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  error: _context2.t2,
                  id: message.id
                });

              case 99:
                if (message.id) {
                  _context2.next = 101;
                  break;
                }

                return _context2.abrupt("return");

              case 101:
                // if login middleware returned true, set connection as authenticated
                if (message.method === "rpc.login" && response === true) {
                  s = this.namespaces[ns].clients.get(socket_id);
                  s["_authenticated"] = true;
                  this.namespaces[ns].clients.set(socket_id, s);
                }

                return _context2.abrupt("return", {
                  jsonrpc: "2.0",
                  result: response,
                  id: message.id
                });

              case 103:
              case "end":
                return _context2.stop();
            }
          }
        }, _callee2, this, [[17, 38, 41, 44], [52, 69, 72, 75], [86, 92]]);
      }));

      function _runMethod(_x2, _x3) {
        return _runMethod2.apply(this, arguments);
      }

      return _runMethod;
    }()
    /**
     * Generate a new namespace store.
     * Also preregister some special namespace methods.
     * @private
     * @param {String} name - namespaces identifier
     * @return {undefined}
     */

  }, {
    key: "_generateNamespace",
    value: function _generateNamespace(name) {
      var _this6 = this;

      this.namespaces[name] = {
        rpc_methods: {
          "__listMethods": {
            fn: function fn() {
              return Object.keys(_this6.namespaces[name].rpc_methods);
            },
            "protected": false
          }
        },
        clients: new Map(),
        events: {}
      };
    }
  }]);
  return Server;
}(_eventemitter.EventEmitter);

exports["default"] = Server;