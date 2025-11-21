package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Packet65ChatRooms extends Packet {
	public static class ChatRoomSnapshot {
		public String name;
		public String owner;
		public List<String> members;
		public boolean isMember;
		public boolean isOwner;
		public boolean isPinned;
	}

	public List<ChatRoomSnapshot> rooms = new ArrayList<ChatRoomSnapshot>();
	public boolean canCreateRooms;
	public String activeRoom = "";

	public Packet65ChatRooms() {}

	public Packet65ChatRooms(List<ChatRoomSnapshot> rooms, boolean canCreate, String activeRoom) {
		if(rooms != null) {
			this.rooms.addAll(rooms);
		}
		this.canCreateRooms = canCreate;
		this.activeRoom = activeRoom != null ? activeRoom : "";
	}

	@Override
	public void a(DataInputStream datainputstream) throws IOException {
		this.rooms.clear();
		short count = datainputstream.readShort();
		for(int i = 0; i < count; ++i) {
			ChatRoomSnapshot snapshot = new ChatRoomSnapshot();
			snapshot.name = Packet.a(datainputstream, 64);
			snapshot.owner = Packet.a(datainputstream, 64);
			int memberCount = datainputstream.readUnsignedShort();
			snapshot.members = new ArrayList<String>();
			for(int m = 0; m < memberCount; ++m) {
				snapshot.members.add(Packet.a(datainputstream, 64));
			}
			snapshot.isMember = datainputstream.readBoolean();
			snapshot.isOwner = datainputstream.readBoolean();
			snapshot.isPinned = datainputstream.readBoolean();
			this.rooms.add(snapshot);
		}
		this.canCreateRooms = datainputstream.readBoolean();
		this.activeRoom = Packet.a(datainputstream, 64);
	}

	@Override
	public void a(DataOutputStream dataoutputstream) throws IOException {
		dataoutputstream.writeShort(this.rooms.size());
		for(ChatRoomSnapshot snapshot : this.rooms) {
			Packet.a(snapshot.name != null ? snapshot.name : "", dataoutputstream);
			Packet.a(snapshot.owner != null ? snapshot.owner : "", dataoutputstream);
			int memberCount = snapshot.members != null ? snapshot.members.size() : 0;
			dataoutputstream.writeShort(memberCount);
			if(snapshot.members != null) {
				for(String member : snapshot.members) {
					Packet.a(member != null ? member : "", dataoutputstream);
				}
			}
			dataoutputstream.writeBoolean(snapshot.isMember);
			dataoutputstream.writeBoolean(snapshot.isOwner);
			dataoutputstream.writeBoolean(snapshot.isPinned);
		}
		dataoutputstream.writeBoolean(this.canCreateRooms);
		Packet.a(this.activeRoom != null ? this.activeRoom : "", dataoutputstream);
	}

	@Override
	public void a(NetHandler nethandler) {
		nethandler.handle65ChatRooms(this);
	}

	@Override
	public int a() {
		return 2 + this.rooms.size() * 20;
	}
}

