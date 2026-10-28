package com.fhasix.uitheme;

/** Duck interface mixed into ItemEntityRenderState so each dropped item remembers its own scale. */
public interface NewScaleHolder {
    float fhasix$getScale();

    void fhasix$setScale(float scale);
}
