-- Nvidia Streaming Video Packet Dissector
-- Version: 1.0
-- Diego Waxemberg

-- video header
local nvHeader_frame = ProtoField.uint32("nv.frame", "Frame", base.HEX) -- 4 bytes
local nvHeader_pindex = ProtoField.uint16("nv.pindex", "Packet Index", base.HEX) -- 2 bytes
local nvHeader_empty = ProtoField.uint16("nv.empty", "Empty?", base.HEX) -- 2 bytes
local nvHeader_pframe = ProtoField.uint16("nv.pframe", "Packets in Frame", base.HEX) -- 2 bytes
local nvHeader_garbage = ProtoField.bytes("nv.garbage", "Garbage") -- 6 bytes
local nvHeader_length = ProtoField.uint16("nv.length", "Length", base.HEX) -- 2 bytes
local nvHeader_moregarbage = ProtoField.bytes("nv.moregarbage", "More Garbage") --23 bytes
local nvHeader_start = ProtoField.uint32("nv.start", "Start", base.HEX) -- 4 bytes

-- video data
local nvVideo_data = ProtoField.bytes("nv.data", "Data") -- rest

p_nv = Proto("nv", "Nvidia Video Stream Protocol")
p_nv.fields = {
    nvHeader_frame,
    nvHeader_pindex,
    nvHeader_empty,
    nvHeader_pframe,
    nvHeader_garbage,
    nvHeader_length,
    nvHeader_moregarbage,
    nvHeader_start,
    nvVideo_data
    }

function p_nv.dissector(buf, pkt, root)
    pkt.cols.protocol = p_nv.name
    subtree = root:add(p_nv, buf(0))
        
    i = 0
    -- frame
    frame = buf(i, 4):le_uint()
    subtree:add(nvHeader_frame, buf(i, 4), frame)
    i = i + 4
    
    --pindex
    pindex = buf(i, 2):le_uint()
    subtree:add(nvHeader_pindex, buf(i, 2), pindex)
    i = i + 2
    
    --empty
    empty = buf(i, 2):le_uint()
    subtree:add(nvHeader_empty, buf(i, 2), empty)
    i = i + 2

    --pframe
    pframe = buf(i, 2):le_uint()
    subtree:add(nvHeader_pframe, buf(i, 2), pframe)
    i = i + 2

    -- garbage
    garbage = buf(i, 6):bytes()
    subtree:add(nvHeader_garbage, buf(i, 6))
    i = i + 6
    
    -- length
    length = buf(i, 2):le_uint()
    subtree:add(nvHeader_length, buf(i, 2), length)
    i = i + 2
    
    -- moregarbage
    moregarbage = buf(i, 23):bytes()
    subtree:add(nvHeader_moregarbage, buf(i, 23))
    i = i + 23
    
    -- start
    start = buf(i, 4):uint()
    subtree:add(nvHeader_start, buf(i, 4), start)
    i = i + 4
    
    -- data
    data = buf(i, buf:len()-i):bytes()
    subtree:add(nvVideo_data, buf(i, buf:len()-i))
end

function p_nv.init()
end


local udp_dissector_table = DissectorTable.get("rtp.pt")
udp_dissector_table:add(96, p_nv)



