package net.minecraft.server;

public class TileEntitySign extends TileEntity {

    public String[] lines = new String[] { "", "", "", "" };
    public int b = -1;
    private boolean isEditable = true;
    private String editingPlayerName;
    
    /** Text color as RGB integer (default black = 0x000000) */
    public int textColor = 0x000000;
    
    /** Dye damage value to RGB color mapping (matches ItemDye order) */
    public static final int[] DYE_COLORS = new int[]{
        0x1D1D21, // 0: Black (ink sac)
        0xB02E26, // 1: Red
        0x5E7C16, // 2: Green
        0x835432, // 3: Brown
        0x3C44AA, // 4: Blue (lapis)
        0x8932B8, // 5: Purple
        0x169C9C, // 6: Cyan
        0x9D9D97, // 7: Light Gray
        0x474F52, // 8: Gray
        0xF38BAA, // 9: Pink
        0x80C71F, // 10: Lime
        0xFED83D, // 11: Yellow
        0x3AB3DA, // 12: Light Blue
        0xC74EBD, // 13: Magenta
        0xF9801D, // 14: Orange
        0xF9FFFE  // 15: White (bonemeal)
    };

    public TileEntitySign() {
    }

    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
        nbttagcompound.setString("Text1", this.lines[0]);
        nbttagcompound.setString("Text2", this.lines[1]);
        nbttagcompound.setString("Text3", this.lines[2]);
        nbttagcompound.setString("Text4", this.lines[3]);
        nbttagcompound.a("Color", this.textColor);
    }

    public void a(NBTTagCompound nbttagcompound) {
        this.isEditable = false;
        this.editingPlayerName = null;
        super.a(nbttagcompound);

        for (int i = 0; i < 4; ++i) {
            this.lines[i] = nbttagcompound.getString("Text" + (i + 1));
            if (this.lines[i].length() > 15) {
                this.lines[i] = this.lines[i].substring(0, 15);
            }
        }
        
        if (nbttagcompound.hasKey("Color")) {
            this.textColor = nbttagcompound.e("Color");
        }
    }

    public Packet f() {
        String[] astring = new String[4];

        for (int i = 0; i < 4; ++i) {
            astring[i] = this.lines[i];

            // CraftBukkit start - limit sign text to 15 chars per line
            if (this.lines[i].length() > 15) {
                astring[i] = this.lines[i].substring(0, 15);
            }
            // CraftBukkit end
        }

        Packet130UpdateSign packet = new Packet130UpdateSign(this.x, this.y, this.z, astring);
        packet.color = this.textColor;
        return packet;
    }

    public boolean a() {
        return this.isEditable;
    }

    public void a(boolean flag) {
        this.isEditable = flag;
        if (!flag) {
            this.editingPlayerName = null;
        }
    }

    /** Bind this newly placed sign's single edit to the player who placed it. */
    void beginEditing(String playerName) {
        this.editingPlayerName = playerName == null || playerName.length() == 0 ? null : playerName;
        this.isEditable = this.editingPlayerName != null;
    }

    /**
     * Atomically consume the one permitted edit. Sign update packets can be
     * replayed, so ownership must be checked and cleared before plugin events.
     */
    boolean finishEditing(String playerName) {
        if (!this.isEditable || this.editingPlayerName == null || playerName == null
                || !this.editingPlayerName.equalsIgnoreCase(playerName)) {
            return false;
        }

        this.isEditable = false;
        this.editingPlayerName = null;
        return true;
    }
    
    /** Convert dye damage value to RGB color */
    public static int dyeDamageToColor(int dyeDamage) {
        if (dyeDamage >= 0 && dyeDamage < DYE_COLORS.length) {
            return DYE_COLORS[dyeDamage];
        }
        return 0x000000; // default black
    }
    
    /** Set color from dye and mark dirty */
    public void setColorFromDye(int dyeDamage) {
        this.textColor = dyeDamageToColor(dyeDamage);
        this.update();
    }
}
