package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet66ChatRoomAction extends Packet {
	public static final byte ACTION_CREATE = 0;
	public static final byte ACTION_JOIN = 1;
	public static final byte ACTION_LEAVE = 2;
	public static final byte ACTION_DELETE = 3;
	public static final byte ACTION_REFRESH = 4;
	public static final byte ACTION_TOGGLE_PIN = 5;
	public static final byte ACTION_KICK = 6;
	public static final byte ACTION_SET_VOICE_ROUTE = 7;

	public byte action;
	public String roomName;
	public String targetName;

	public Packet66ChatRoomAction() {}

	public Packet66ChatRoomAction(byte action, String roomName) {
		this.action = action;
		this.roomName = roomName != null ? roomName : "";
		this.targetName = "";
	}

	public Packet66ChatRoomAction(byte action, String roomName, String targetName) {
		this.action = action;
		this.roomName = roomName != null ? roomName : "";
		this.targetName = targetName != null ? targetName : "";
	}

	@Override
	public void a(DataInputStream datainputstream) throws IOException {
		this.action = datainputstream.readByte();
		this.roomName = Packet.a(datainputstream, 64);
		this.targetName = Packet.a(datainputstream, 64);
	}

	@Override
	public void a(DataOutputStream dataoutputstream) throws IOException {
		dataoutputstream.writeByte(this.action);
		Packet.a(this.roomName != null ? this.roomName : "", dataoutputstream);
		Packet.a(this.targetName != null ? this.targetName : "", dataoutputstream);
	}

	@Override
	public void a(NetHandler nethandler) {
		nethandler.handle66ChatRoomAction(this);
	}

	@Override
	public int a() {
		return 1 + (this.roomName != null ? this.roomName.length() : 0) + (this.targetName != null ? this.targetName.length() : 0);
	}
}
