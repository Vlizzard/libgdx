/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ActorEvent;
import com.badlogic.gdx.scenes.scene2d.ActorListener;
import com.badlogic.gdx.scenes.scene2d.utils.Cullable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;

/** A group that scrolls a child widget using scroll bars.
 * <p>
 * The widget is sized to its preferred size. If the widget's preferred width or height is less than the size of this scroll pane,
 * it is set to the size of this scroll pane. Scrollbars appear when the widget is larger than the scroll pane.
 * <p>
 * The scroll pane's preferred size is that of the child widget. At this size, the child widget will not need to scroll, so the
 * scroll pane is typically sized by ignoring the preferred size in one or both directions.
 * @author mzechner
 * @author Nathan Sweet */
public class ScrollPane extends WidgetGroup {
	private ScrollPaneStyle style;
	private Actor widget;

	final Rectangle hScrollBounds = new Rectangle();
	final Rectangle vScrollBounds = new Rectangle();
	final Rectangle hKnobBounds = new Rectangle();
	final Rectangle vKnobBounds = new Rectangle();
	private final Rectangle widgetAreaBounds = new Rectangle();
	private final Rectangle widgetCullingArea = new Rectangle();
	private final Rectangle scissorBounds = new Rectangle();

	boolean scrollX, scrollY;
	private float amountX, amountY;
	boolean touchScrollH, touchScrollV;
	final Vector2 lastPoint = new Vector2();
	float handlePosition;
	private boolean disableX, disableY;
	private float areaWidth, areaHeight;

	public ScrollPane (Skin skin) {
		this(null, skin);
	}

	/** @param widget May be null. */
	public ScrollPane (Actor widget, Skin skin) {
		this(widget, skin.getStyle(ScrollPaneStyle.class));
	}

	/** @param widget May be null. */
	public ScrollPane (Actor widget, ScrollPaneStyle style) {
		if (style == null) throw new IllegalArgumentException("style cannot be null.");
		this.widget = widget;
		this.style = style;
		if (widget != null) {
			setWidget(widget);
		}
		setWidth(150);
		setHeight(150);

		addListener(new ActorListener() {
			public boolean touchDown (ActorEvent event, float x, float y, int pointer, int button) {
				if (pointer != 0) return false;

				if (scrollX && hScrollBounds.contains(x, y)) {
					if (hKnobBounds.contains(x, y)) {
						lastPoint.set(x, y);
						handlePosition = hKnobBounds.x;
						touchScrollH = true;
						return true;
					}
					if (x < hKnobBounds.x)
						setScrollPercentX(Math.max(0, getScrollPercentX() - 0.1f));
					else
						setScrollPercentX(Math.min(1, getScrollPercentX() + 0.1f));
					return false;
				} else if (scrollY && vScrollBounds.contains(x, y)) {
					if (vKnobBounds.contains(x, y)) {
						lastPoint.set(x, y);
						handlePosition = vKnobBounds.y;
						touchScrollV = true;
						return true;
					}
					if (y < vKnobBounds.y)
						setScrollPercentY(Math.max(0, getScrollPercentY() + 0.1f));
					else
						setScrollPercentY(Math.min(1, getScrollPercentY() - 0.1f));
					return false;
				}
				return false;
			}

			public void touchUp (ActorEvent event, float x, float y, int pointer, int button) {
				touchScrollH = false;
				touchScrollV = false;
			}

			public void touchDragged (ActorEvent event, float x, float y, int pointer) {
				if (touchScrollH) {
					float delta = x - lastPoint.x;
					float scrollH = handlePosition + delta;
					handlePosition = scrollH;
					scrollH = Math.max(hScrollBounds.x, scrollH);
					scrollH = Math.min(hScrollBounds.x + hScrollBounds.width - hKnobBounds.width, scrollH);
					setScrollPercentX((scrollH - hScrollBounds.x) / (hScrollBounds.width - hKnobBounds.width));
					lastPoint.set(x, y);
				} else if (touchScrollV) {
					float delta = y - lastPoint.y;
					float scrollV = handlePosition + delta;
					handlePosition = scrollV;
					scrollV = Math.max(vScrollBounds.y, scrollV);
					scrollV = Math.min(vScrollBounds.y + vScrollBounds.height - vKnobBounds.height, scrollV);
					setScrollPercentY(1 - ((scrollV - vScrollBounds.y) / (vScrollBounds.height - vKnobBounds.height)));
					lastPoint.set(x, y);
				}
			}
		});
	}

	public void setStyle (ScrollPaneStyle style) {
		if (style == null) throw new IllegalArgumentException("style cannot be null.");
		this.style = style;
		invalidateHierarchy();
	}

	/** Returns the scroll pane's style. Modifying the returned style may not have an effect until
	 * {@link #setStyle(ScrollPaneStyle)} is called. */
	public ScrollPaneStyle getStyle () {
		return style;
	}

	public void layout () {
		final Drawable bg = style.background;
		final Drawable hScrollKnob = style.hScrollKnob;
		final Drawable vScrollKnob = style.vScrollKnob;

		// For no background, ex. background is drawn a parent who has two scroll area
		float bgLeftWidth = bg == null ? 0 : bg.getLeftWidth();
		float bgRightWidth = bg == null ? 0 : bg.getRightWidth();
		float bgTopHeight = bg == null ? 0 : bg.getTopHeight();
		float bgBottomHeight = bg == null ? 0 : bg.getTopHeight();

		float width = getWidth();
		float height = getHeight();

		// Get available space size by subtracting background's padded area.
		areaWidth = width - bgLeftWidth - bgRightWidth;
		areaHeight = height - bgTopHeight - bgBottomHeight;

		if (widget == null) return;

		// Get widget's desired width.
		float widgetWidth, widgetHeight;
		if (widget instanceof Layout) {
			Layout layout = (Layout)widget;
			widgetWidth = layout.getPrefWidth();
			widgetHeight = layout.getPrefHeight();
		} else {
			widgetWidth = widget.getWidth();
			widgetHeight = widget.getHeight();
		}

		// Figure out if we need horizontal/vertical scrollbars.
		scrollX = false;
		scrollY = false;
		if (!disableX && widgetWidth > areaWidth) scrollX = true;
		if (!disableY && widgetHeight > areaHeight) scrollY = true;

		// Check again, now taking into account the area that's taken up by any enabled scrollbars.
		if (!disableX && scrollY && widgetWidth > areaWidth - vScrollKnob.getMinWidth()) {
			scrollX = true;
			areaWidth -= vScrollKnob.getMinWidth();
		}
		if (!disableY && scrollX && widgetHeight > areaHeight - hScrollKnob.getMinHeight()) {
			scrollY = true;
			areaHeight -= hScrollKnob.getMinHeight();
		}

		// Set the widget area bounds.
		widgetAreaBounds.set(bgLeftWidth, bgBottomHeight + (scrollX ? hScrollKnob.getMinHeight() : 0), areaWidth, areaHeight);
		amountX = MathUtils.clamp(amountX, 0, widgetAreaBounds.x);
		amountY = MathUtils.clamp(amountY, 0, widgetAreaBounds.y);

		// If the widget is smaller than the available space, make it take up the available space.
		widgetWidth = disableX ? width : Math.max(areaWidth, widgetWidth);
		widgetHeight = disableY ? height : Math.max(areaHeight, widgetHeight);
		if (widget.getWidth() != widgetWidth || widget.getHeight() != widgetHeight) {
			widget.setWidth(widgetWidth);
			widget.setHeight(widgetHeight);
		}

		// Set the bounds and scroll knob sizes if scrollbars are needed.
		if (scrollX) {
			hScrollBounds.set(bgLeftWidth, bgBottomHeight, areaWidth, hScrollKnob.getMinHeight());
			hKnobBounds.width = Math.max(hScrollKnob.getMinWidth(), (int)(hScrollBounds.width * areaWidth / widget.getWidth()));
			hKnobBounds.height = hScrollKnob.getMinHeight();
			hKnobBounds.x = hScrollBounds.x + (int)((hScrollBounds.width - hKnobBounds.width) * getScrollPercentX());
			hKnobBounds.y = hScrollBounds.y;
		}
		if (scrollY) {
			vScrollBounds.set(width - bgRightWidth - vScrollKnob.getMinWidth(), height - bgTopHeight - areaHeight,
				vScrollKnob.getMinWidth(), areaHeight);
			vKnobBounds.width = vScrollKnob.getMinWidth();
			vKnobBounds.height = Math.max(vScrollKnob.getMinHeight(), (int)(vScrollBounds.height * areaHeight / widget.getHeight()));
			vKnobBounds.x = vScrollBounds.x;
			vKnobBounds.y = vScrollBounds.y + (int)((vScrollBounds.height - vKnobBounds.height) * (1 - getScrollPercentY()));
		}

		if (widget instanceof Layout) {
			Layout layout = (Layout)widget;
			layout.invalidate();
			layout.validate();
		}
	}

	@Override
	public void draw (SpriteBatch batch, float parentAlpha) {
		if (widget == null) return;

		validate();

		// Setup transform for this group.
		applyTransform(batch);

		if (scrollX) hKnobBounds.x = hScrollBounds.x + (int)((hScrollBounds.width - hKnobBounds.width) * getScrollPercentX());
		if (scrollY)
			vKnobBounds.y = vScrollBounds.y + (int)((vScrollBounds.height - vKnobBounds.height) * (1 - getScrollPercentY()));

		// Calculate the widgets offset depending on the scroll state and available widget area.
		widget.setY(widgetAreaBounds.y - (!scrollY ? (int)(widget.getHeight() - areaHeight) : 0)
			- (scrollY ? (int)((widget.getHeight() - areaHeight) * (1 - getScrollPercentY())) : 0));
		widget.setX(widgetAreaBounds.x - (scrollX ? (int)((widget.getWidth() - areaWidth) * getScrollPercentX()) : 0));
		if (widget instanceof Cullable) {
			widgetCullingArea.x = -widget.getX() + widgetAreaBounds.x;
			widgetCullingArea.y = -widget.getY() + widgetAreaBounds.y;
			widgetCullingArea.width = areaWidth;
			widgetCullingArea.height = areaHeight;
			((Cullable)widget).setCullingArea(widgetCullingArea);
		}

		// Caculate the scissor bounds based on the batch transform, the available widget area and the camera transform. We need to
		// project those to screen coordinates for OpenGL ES to consume.
		ScissorStack.calculateScissors(getStage().getCamera(), batch.getTransformMatrix(), widgetAreaBounds, scissorBounds);

		// Draw the background ninepatch.
		Color color = getColor();
		batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
		if (style.background != null) style.background.draw(batch, 0, 0, getWidth(), getHeight());
		batch.flush();

		// Enable scissors for widget area and draw the widget.
		if (ScissorStack.pushScissors(scissorBounds)) {
			drawChildren(batch, parentAlpha);
			ScissorStack.popScissors();
		}

		// Render scrollbars and knobs on top.
		batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
		if (scrollX) {
			style.hScroll.draw(batch, hScrollBounds.x, hScrollBounds.y, hScrollBounds.width, hScrollBounds.height);
			style.hScrollKnob.draw(batch, hKnobBounds.x, hKnobBounds.y, hKnobBounds.width, hKnobBounds.height);
		}
		if (scrollY) {
			style.vScroll.draw(batch, vScrollBounds.x, vScrollBounds.y, vScrollBounds.width, vScrollBounds.height);
			style.vScrollKnob.draw(batch, vKnobBounds.x, vKnobBounds.y, vKnobBounds.width, vKnobBounds.height);
		}

		resetTransform(batch);
	}

	public float getPrefWidth () {
		if (widget instanceof Layout) return ((Layout)widget).getPrefWidth();
		return 150;
	}

	public float getPrefHeight () {
		if (widget instanceof Layout) return ((Layout)widget).getPrefHeight();
		return 150;
	}

	public float getMinWidth () {
		return 0;
	}

	public float getMinHeight () {
		return 0;
	}

	/** Sets the {@link Actor} embedded in this scroll pane.
	 * @param widget the Actor */
	public void setWidget (Actor widget) {
		if (widget == null) throw new IllegalArgumentException("widget cannot be null.");
		if (this.widget != null) super.removeActor(this.widget);
		this.widget = widget;
		if (widget != null) super.addActor(widget);
	}

	public void addActor (Actor actor) {
		throw new UnsupportedOperationException("Use ScrollPane#setWidget.");
	}

	public void addActorAt (int index, Actor actor) {
		throw new UnsupportedOperationException("Use ScrollPane#setWidget.");
	}

	public void addActorBefore (Actor actorBefore, Actor actor) {
		throw new UnsupportedOperationException("Use ScrollPane#setWidget.");
	}

	public boolean removeActor (Actor actor) {
		throw new UnsupportedOperationException("Use ScrollPane#setWidget(null).");
	}

	public Actor hit (float x, float y) {
		if (x > 0 && x < getWidth() && y > 0 && y < getHeight()) return super.hit(x, y);
		return null;
	}

	public void setScrollX (float pixels) {
		this.amountX = pixels;
	}

	/** Returns the x scroll position in pixels. */
	public float getScrollX () {
		return amountX;
	}

	public void setScrollY (float pixels) {
		amountY = pixels;
	}

	/** Returns the y scroll position in pixels. */
	public float getScrollY () {
		return amountY;
	}

	public float getScrollPercentX () {
		return amountX / widgetAreaBounds.x;
	}

	public void setScrollPercentX (float percentX) {
		amountX = widgetAreaBounds.x * percentX;
	}

	public float getScrollPercentY () {
		return amountY / widgetAreaBounds.y;
	}

	public void setScrollPercentY (float percentY) {
		amountY = widgetAreaBounds.y * percentY;
	}

	/** Sets the scroll offset so the specified rectangle is fully in view. */
	public void scrollTo (float x, float y, float width, float height) {
		float paneWidth = getWidth();
		float paneHeight = getHeight();

		if (x < amountX)
			amountX = x;
		else if (x + width > amountX + paneWidth) //
			amountX = x + width - paneWidth;

		y = getMaxY() + paneHeight - y;
		if (y > amountY + paneHeight)
			amountY = y - paneHeight;
		else if (y - height < amountY) //
			amountY = y - height;
	}

	/** Returns the maximum scroll value in the x direction. */
	public float getMaxX () {
		return widgetAreaBounds.x;
	}

	/** Returns the maximum scroll value in the y direction. */
	public float getMaxY () {
		return widgetAreaBounds.y;
	}

	/** Disables scrolling in a direction. The widget will be sized to the FlickScrollPane in the disabled direction. */
	public void setScrollingDisabled (boolean x, boolean y) {
		disableX = x;
		disableY = y;
	}

	/** The style for a scroll pane, see {@link ScrollPane}.
	 * @author mzechner
	 * @author Nathan Sweet */
	static public class ScrollPaneStyle {
		/** Optional. */
		public Drawable background;
		public Drawable hScroll;
		public Drawable hScrollKnob;
		public Drawable vScroll;
		public Drawable vScrollKnob;

		public ScrollPaneStyle () {
		}

		public ScrollPaneStyle (Drawable background, Drawable hScroll, Drawable hScrollKnob, Drawable vScroll, Drawable vScrollKnob) {
			this.background = background;
			this.hScroll = hScroll;
			this.hScrollKnob = hScrollKnob;
			this.vScroll = vScroll;
			this.vScrollKnob = vScrollKnob;
		}

		public ScrollPaneStyle (ScrollPaneStyle style) {
			this.background = style.background;
			this.hScroll = style.hScroll;
			this.hScrollKnob = style.hScrollKnob;
			this.vScroll = style.vScroll;
			this.vScrollKnob = style.vScrollKnob;
		}
	}
}
