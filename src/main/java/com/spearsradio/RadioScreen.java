package com.spearsradio;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class RadioScreen extends Screen {
    private static final int ROWS = 6;
    private int scroll = 0;
    private TextFieldWidget field;

    public RadioScreen() { super(Text.literal("Spears Radio")); }

    @Override
    protected void init() {
        int cx = width / 2, left = cx - 150, y = 28;
        field = new TextFieldWidget(textRenderer, left, y, 232, 20, Text.literal("URL"));
        field.setMaxLength(1024);
        field.setPlaceholder(Text.literal("Paste MP3 stream or .m3u/.pls URL"));
        addDrawableChild(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> {
            RadioPlayer.addUrl(field.getText(), () -> {});
            field.setText("");
        }).dimensions(left + 236, y, 64, 20).build());

        // playlist rows (click = play)
        for (int r = 0; r < ROWS; r++) {
            int i = scroll + r;
            if (i >= RadioPlayer.list.size()) break;
            final int idx = i;
            String label = (idx == RadioPlayer.index ? "> " : "") + RadioPlayer.list.get(idx).name();
            if (label.length() > 38) label = label.substring(0, 38) + "...";
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> { RadioPlayer.play(idx); clearAndInit(); })
                .dimensions(left, y + 28 + r * 22, 262, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { RadioPlayer.remove(idx); clearAndInit(); })
                .dimensions(left + 266, y + 28 + r * 22, 34, 20).build());
        }
        int sy = y + 28 + ROWS * 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("Up"), b -> { scroll = Math.max(0, scroll - 1); clearAndInit(); })
            .dimensions(left, sy, 48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Down"), b -> {
            if (scroll + ROWS < RadioPlayer.list.size()) scroll++; clearAndInit(); })
            .dimensions(left + 52, sy, 48, 20).build());

        int cy = sy + 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Prev"), b -> RadioPlayer.prev()).dimensions(left, cy, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play/Pause"), b -> RadioPlayer.toggle()).dimensions(left + 76, cy, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"), b -> RadioPlayer.next()).dimensions(left + 230, cy, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Repeat: " + RadioPlayer.repeatName()), b -> {
            RadioPlayer.cycleRepeat(); b.setMessage(Text.literal("Repeat: " + RadioPlayer.repeatName())); })
            .dimensions(left, cy + 26, 300, 20).build());
        addDrawableChild(new SliderWidget(left, cy + 52, 300, 20, Text.literal("Volume: " + (int) (RadioPlayer.volume * 100) + "%"), RadioPlayer.volume) {
            @Override protected void updateMessage() { setMessage(Text.literal("Volume: " + (int) (value * 100) + "%")); }
            @Override protected void applyValue() { RadioPlayer.setVolume((float) value); RadioPlayer.save(); }
        });
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(left, cy + 78, 300, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(RadioPlayer.status), width / 2, height - 14, 0xFFBBBBBB);
    }

    @Override public boolean shouldPause() { return false; }
}
