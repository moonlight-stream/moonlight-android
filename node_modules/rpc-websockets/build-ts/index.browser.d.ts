import CommonClient from "./lib/client";
import { IWSClientAdditionalOptions } from "./lib/client/client.types";
export declare class Client extends CommonClient {
    constructor(address?: string, { autoconnect, reconnect, reconnect_interval, max_reconnects }?: IWSClientAdditionalOptions, generate_request_id?: (method: string, params: object | Array<any>) => number);
}
