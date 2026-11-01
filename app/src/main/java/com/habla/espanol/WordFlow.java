package com.habla.espanol;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

final class WordFlow extends ViewGroup {
    private final int gap;
    WordFlow(Context context) {
        super(context);
        gap = (int) (8 * getResources().getDisplayMetrics().density + .5f);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }
    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int x = 0, y = getPaddingTop(), rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), heightMeasureSpec);
            int cw = child.getMeasuredWidth(), ch = child.getMeasuredHeight();
            if (x > 0 && x + cw > available) { x = 0; y += rowHeight + gap; rowHeight = 0; }
            x += cw + gap; rowHeight = Math.max(rowHeight, ch);
        }
        int height = y + rowHeight + getPaddingBottom();
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(Math.max(height, getSuggestedMinimumHeight()), heightMeasureSpec));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int available = getWidth() - getPaddingLeft() - getPaddingRight();
        int x = 0, y = getPaddingTop(), rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth(), ch = child.getMeasuredHeight();
            if (x > 0 && x + cw > available) { x = 0; y += rowHeight + gap; rowHeight = 0; }
            child.layout(getPaddingLeft() + x, y, getPaddingLeft() + x + cw, y + ch);
            x += cw + gap; rowHeight = Math.max(rowHeight, ch);
        }
    }
}
