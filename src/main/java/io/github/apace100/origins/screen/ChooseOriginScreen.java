package io.github.apace100.origins.screen;

import io.github.apace100.origins.Origins;
import io.github.apace100.origins.networking.packet.c2s.ChooseOriginC2SPacket;
import io.github.apace100.origins.networking.packet.c2s.ChooseRandomOriginC2SPacket;
import io.github.apace100.origins.origin.*;
import io.github.apace100.origins.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChooseOriginScreen extends OriginDisplayScreen {

	private final List<OriginLayer> layerList;
	private final List<Origin> originSelection;

	private final int currentLayerIndex;

	private Origin randomOrigin;

	private int currentOriginIndex = 0;
	private int maxSelection = 0;

	
	public ChooseOriginScreen(List<OriginLayer> layerList, int currentLayerIndex, boolean showDirtBackground) {
		super(Text.translatable(Origins.MODID + ".screen.choose_origin"), showDirtBackground);

		this.layerList = layerList;
		this.currentLayerIndex = currentLayerIndex;
		this.originSelection = new ArrayList<>(layerList.size());

		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return;
		}

		OriginLayer currentLayer = getCurrentLayer();
		currentLayer.getOrigins(player).forEach(originId -> {

			Origin origin = OriginManager.get(originId);
			if (!origin.isChoosable()) {
				return;
			}

			ItemStack iconStack = origin.getDisplayItem();
			if (iconStack.isOf(Items.PLAYER_HEAD) && !iconStack.contains(DataComponentTypes.PROFILE)) {
				iconStack.set(DataComponentTypes.PROFILE, new ProfileComponent(player.getGameProfile()));
			}

			originSelection.add(origin);

		});

		originSelection.sort(Comparator.comparingInt((Origin o) -> o.getImpact().getImpactValue()).thenComparingInt(Origin::getOrder));
		maxSelection = currentLayer.getOriginOptionCount(player);

		if (maxSelection == 0) {
			openNextLayerScreen();
		}

		Origin newOrigin = getCurrentOrigin();
		showOrigin(newOrigin, getCurrentLayer(), newOrigin == randomOrigin);

	}

	private void openNextLayerScreen() {
		MinecraftClient.getInstance().setScreen(new WaitForNextLayerScreen(layerList, currentLayerIndex, this.showDirtBackground));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {

		super.init();
		if (maxSelection <= 0) {
			return;
		}

		//	Draw the select origin button
		addDrawableChild(ButtonWidget.builder(
			Text.translatable(Origins.MODID + ".gui.select"),
			button -> {

				Identifier originId = super.getCurrentOrigin().getId();
				Identifier layerId = getCurrentLayer().getId();

				if (currentOriginIndex == originSelection.size()) {
					ClientPlayNetworking.send(new ChooseRandomOriginC2SPacket(layerId));
				} else {
					ClientPlayNetworking.send(new ChooseOriginC2SPacket(layerId, originId));
				}

				openNextLayerScreen();

			}
		).dimensions(guiLeft + WINDOW_WIDTH / 2 - 50, guiTop + WINDOW_HEIGHT + 5, 100, 20).build());

		if (maxSelection <= 1) {
			return;
		}

		//	Draw the previous origin button
		addDrawableChild(ButtonWidget.builder(
			Text.of("<"),
			button -> {

				currentOriginIndex = (currentOriginIndex - 1 + maxSelection) % maxSelection;
				Origin newOrigin = getCurrentOrigin();

				showOrigin(newOrigin, getCurrentLayer(), newOrigin == randomOrigin);

			}
		).dimensions(guiLeft - 40, height / 2 - 10, 20, 20).build());

		//	Draw the next origin button
		addDrawableChild(ButtonWidget.builder(
			Text.of(">"),
			button -> {

				currentOriginIndex = (currentOriginIndex + 1) % maxSelection;
				Origin newOrigin = getCurrentOrigin();

				showOrigin(newOrigin, getCurrentLayer(), newOrigin == randomOrigin);

			}
		).dimensions(guiLeft + WINDOW_WIDTH + 20, height / 2 - 10, 20, 20).build());

	}

	@Override
	public OriginLayer getCurrentLayer() {
		return layerList.get(currentLayerIndex);
	}

	@Override
	public Origin getCurrentOrigin() {

		if (currentOriginIndex == originSelection.size()) {

			if (randomOrigin == null) {
				initRandomOrigin();
			}

			return randomOrigin;

		}

		return originSelection.get(currentOriginIndex);

	}

	@Override
	protected Text getTitleText() {
		return super.getCurrentLayer().getChooseOriginTitle();
	}

	private void initRandomOrigin() {

		this.randomOrigin = Origin.special(Origins.identifier("random"), ModItems.ORB_OF_ORIGIN.getDefaultStack(), Impact.NONE, -1);

		MutableText randomOriginText = Text.of("").copy();
		List<Identifier> randoms = layerList.get(currentLayerIndex).getRandomOrigins(MinecraftClient.getInstance().player);

		randoms.sort((ia, ib) -> {

			Origin a = OriginManager.get(ia);
			Origin b = OriginManager.get(ib);

			int impactDelta = Integer.compare(a.getImpact().getImpactValue(), b.getImpact().getImpactValue());
			return impactDelta != 0 ? impactDelta : Integer.compare(a.getOrder(), b.getOrder());

		});

		for(Identifier id : randoms) {
			randomOriginText.append(OriginManager.get(id).getName());
			randomOriginText.append(Text.of("\n"));
		}

		setRandomOriginText(randomOriginText);

	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {

		if (maxSelection == 0) {
			openNextLayerScreen();
		} else {
			super.render(context, mouseX, mouseY, delta);
		}

	}

}
