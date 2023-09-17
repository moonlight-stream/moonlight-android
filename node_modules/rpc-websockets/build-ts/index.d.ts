import CommonClient from "./lib/client";
import { NodeWebSocketTypeOptions, IWSClientAdditionalOptions } from "./lib/client/client.types";
export declare class Client extends CommonClient {
    constructor(address?: string, { autoconnect, reconnect, reconnect_interval, max_reconnects, ...rest_options }?: IWSClientAdditionalOptions & NodeWebSocketTypeOptions, generate_request_id?: (method: string, params: object | Array<any>) => number);
}
export { default as Server } from "./lib/server";
