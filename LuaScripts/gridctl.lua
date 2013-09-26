local pf_type = ProtoField.uint16("gridctl.type", "Packet Type", base.HEX)
local pf_paylen = ProtoField.uint32("gridctl.paylen", "Payload Length", base.DEC)
local pf_payload = ProtoField.bytes("gridctl.payload", "Payload bytes")
local pf_payload32 = ProtoField.uint32("gridctl.payload32", "Payload as 32-bit LE integer", base.DEC)
local pf_payload16 = ProtoField.uint16("gridctl.payload16", "Payload as 16-bit LE integer", base.HEX)
local pf_response = ProtoField.uint16("gridctl.response", "Response code", base.HEX)
local pf_origtype = ProtoField.uint16("gridctl.origtype", "Original request type", base.HEX)

p_gridctl = Proto ("GridCtl", "Nvidia GRID Control Protocol")

p_gridctl.fields = {
    pf_type,
    pf_paylen,
    pf_payload,
    pf_payload16,
    pf_payload32,
    pf_response,
    pf_origtype
}

function p_gridctl.dissector(buf, pkt, root)
    pkt.cols.protocol = p_gridctl.name
    
    subtree = root:add(p_gridctl, buf(0))
    
    -- We can have multiple GRID control packets in one TCP datagram
    i = 0
    while i < buf:len() do
        -- Make sure we have a full header
        if buf:len() - i < 4 then
            -- We need more data
            pkt.desegment_len = DESEGMENT_ONE_MORE_SEGMENT
            pkt.desegment_offset = 0
        end
        
        -- Read the packet type field
        ptype = buf(i, 2):le_uint()
        subtree:add(pf_type, buf(i, 2), ptype)
        i = i + 2
        
        -- Read the payload length field
        paylen = buf(i, 2):le_uint()
        subtree:add(pf_paylen, buf(i, 2), paylen)
        i = i + 2
        
        -- Make sure we have the full payload
        if buf:len() - i < paylen then
            -- We need more data
            pkt.desegment_len = DESEGMENT_ONE_MORE_SEGMENT
            pkt.desegment_offset = 0
        end
        
        -- Decode responses differently
        if ptype == 0x0204 then
            origtype = buf(i, 2):le_uint()
            subtree:add(pf_origtype, buf(i, 2), origtype)
            i = i + 2
            response = buf(i, 2):le_uint()
            subtree:add(pf_response, buf(i, 2), response)
            i = i + 2
        else
            if paylen == 4 then
                -- Display the payload as a uint32 (LE)
                payload32 = buf(i, 4):le_uint()
                subtree:add(pf_payload32, buf(i, 4), payload32)
            elseif paylen == 2 then
                -- Display the payload as a uint16 (LE)
                payload16 = buf(i, 2):le_uint()
                subtree:add(pf_payload16, buf(i, 2), payload16)
            elseif paylen ~= 0 then
                subtree:add(pf_payload, buf(i, paylen))
            end
            i = i + paylen
        end

        if i ~= buf:len() then
            subtree:add("")
        end
    end
end

function p_gridctl.init()
end

local tcp_dissector_table = DissectorTable.get("tcp.port")
tcp_dissector_table:add(47995, p_gridctl)