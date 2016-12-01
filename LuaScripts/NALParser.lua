-- H264 NAL Parser
-- Version: 1.0
-- Cameron Gutman

-- NAL header
local nal_start = ProtoField.bytes("nal.start", "H264 NAL Start Sequence") -- 4 Byte Start
local nal_type = ProtoField.uint8("nal.type", "H264 NAL Type") -- 1 byte NAL type
local nal_data = ProtoField.bytes("nal.data", "H264 NAL Data") -- variable byte NAL data

p_h264raw = Proto("h264raw", "H264 Raw NAL Parser")
p_h264raw.fields = {
    nal_start,
	nal_type,
	nal_data
    }

function p_h264raw.dissector(buf, pkt, root)
    pkt.cols.protocol = p_h264raw.name
    subtree = root:add(p_h264raw, buf(0))

	local i = 0
	local data_start = -1
    while i < buf:len() do
		-- Make sure we have a potential start sequence and type
        if buf:len() - i < 5 then
            -- We need more data
            pkt.desegment_len = DESEGMENT_ONE_MORE_SEGMENT
            pkt.desegment_offset = 0
			return
        end
	
		-- Check for start sequence
		start = buf(i, 4):uint()
		if start == 1 then
			if data_start ~= -1 then
				-- End the last NAL
				subtree:add(nal_data, buf(data_start, i-data_start))
			end
			-- This is the start of a NAL
			subtree:add(nal_start, buf(i, 4))
			i = i + 4
			-- Next byte is NAL type
			subtree:add(nal_type, buf(i, 1), buf(i, 1):uint8())
			i = i + 1
			-- Data begins here
			data_start = i
		else
			-- This must be a data byte
			i = i + 1
		end
	end
end

function p_h264raw.init()
end


local udp_dissector_table = DissectorTable.get("rtp.pt")
udp_dissector_table:add(96, p_h264raw)



