//
// Triple Play - utilities for use in PlayN-based games
// Copyright (c) 2011-2012, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/tripleplay/blob/master/LICENSE

package tripleplay.platform;

import java.util.HashMap;
import java.util.Map;

import cli.MonoTouch.CoreGraphics.CGAffineTransform;
import cli.MonoTouch.Foundation.NSNotification;
import cli.MonoTouch.Foundation.NSNotificationCenter;
import cli.MonoTouch.Foundation.NSValue;
import cli.MonoTouch.UIKit.UIFont;
import cli.MonoTouch.UIKit.UIKeyboard;
import cli.MonoTouch.UIKit.UITextField;
import cli.MonoTouch.UIKit.UIView;
import cli.System.Drawing.PointF;
import cli.System.Drawing.RectangleF;
import cli.System.Drawing.SizeF;

import playn.core.Font;
import playn.ios.IOSFont;

import static tripleplay.platform.Log.log;

/**
 * Handles shared bits for native text fields.
 */
public class IOSTextFieldHandler
{
    public IOSTextFieldHandler (IOSTPPlatform platform) {
        _overlay = platform.platform.uiOverlay();

        // update text values when the text is changed
        NSNotificationCenter.get_DefaultCenter().AddObserver(
            UITextField.get_TextFieldTextDidChangeNotification(),
            new cli.System.Action$$00601_$$$_Lcli__MonoTouch__Foundation__NSNotification_$$$$_(new cli.System.Action$$00601_$$$_Lcli__MonoTouch__Foundation__NSNotification_$$$$_.Method() {
                @Override public void Invoke (NSNotification nf) {
                    // we get notifications about all text fields, whether they're under our
                    // control or not
                    IOSNativeTextField field = _activeFields.get(nf.get_Object());
                    if (field != null) field.text().update(field._field.get_Text());
                }}));

        // fire the finishedEditing signal when editing is ended
        NSNotificationCenter.get_DefaultCenter().AddObserver(
            UITextField.get_TextDidEndEditingNotification(),
            new cli.System.Action$$00601_$$$_Lcli__MonoTouch__Foundation__NSNotification_$$$$_(
                new cli.System.Action$$00601_$$$_Lcli__MonoTouch__Foundation__NSNotification_$$$$_.Method() {
                    @Override public void Invoke (NSNotification nf) {
                        IOSNativeTextField field = _activeFields.get(nf.get_Object());
                        if (field != null) {
                            field._finishedEditing.emit(null);
                        }
                    }
                }));

        // slide the game view up when the keyboard is displayed
        NSNotificationCenter.get_DefaultCenter().AddObserver(
            UIKeyboard.get_DidShowNotification(),
            new cli.System.Action$$00601_$$$_Lcli__MonoTouch__Foundation__NSNotification_$$$$_(new cli.System.Action$$00601_$$$_Lcli__MonoTouch__Foundation__NSNotification_$$$$_.Method() {
                @Override public void Invoke (NSNotification nf) {
                    if (_gameViewTransformed) {
                        // already transformed, bail with a warning
                        log.warning("Keyboard shown when already showing?", "viewTransform",
                                    _gameViewTransform);
                        return;
                    }

                    // find the first responder
                    UITextField firstResponder = null;
                    for (UITextField field : _activeFields.keySet()) {
                        if (field.get_IsFirstResponder()) {
                            firstResponder = field;
                            break;
                        }
                    }
                    if (firstResponder == null) {
                        return; // it's not a field we're managing, bail
                    }

                    // figure out how we need to transform the game view
                    SizeF size = ((NSValue) nf.get_UserInfo().get_Item(
                        UIKeyboard.get_FrameBeginUserInfoKey())).get_RectangleFValue().get_Size();
                    RectangleF fieldFrame = firstResponder.get_Frame();
                    // oddly, the size given for keyboard dimensions is portrait, always.
                    float targetOffset = -size.get_Width() +
                        _overlay.get_Bounds().get_Height() - fieldFrame.get_Bottom();
                    // give it a little padding, and make sure we never move the game view down,
                    // also make sure we never move the bottom of the game view past the top of the
                    // keyboard
                    targetOffset = Math.max(Math.min(targetOffset - 10, 0), -size.get_Width());
                    PointF target = new PointF(0, targetOffset);
                    target = _overlay.get_Transform().TransformPoint(target);

                    // update and set the transform on the game view
                    UIView gameView = _overlay.get_Superview();
                    CGAffineTransform trans = gameView.get_Transform();
                    _gameViewTransform = trans.Invert().Invert(); // clone
                    trans.Translate(target.get_X(), target.get_Y());
                    gameView.set_Transform(trans);
                    _gameViewTransformed = true;
                }}));

        NSNotificationCenter.get_DefaultCenter().AddObserver(
            UIKeyboard.get_WillHideNotification(),
            new cli.System.Action$$00601_$$$_Lcli__MonoTouch__Foundation__NSNotification_$$$$_(new cli.System.Action$$00601_$$$_Lcli__MonoTouch__Foundation__NSNotification_$$$$_.Method() {
                @Override public void Invoke (NSNotification nf) {
                    if (!_gameViewTransformed) {
                        return; // not transformed? bail - this might be ok, if it was shown
                                // outside of our purview
                    }

                    UIView gameView = _overlay.get_Superview();
                    gameView.set_Transform(_gameViewTransform);
                    _gameViewTransform = null;
                    _gameViewTransformed = false;
                }}));
    }

    public UIFont getUIFont (Font font) {
        if (font == null) font = IOSFont.defaultFont();

        String iosName = ((IOSFont)font).iosName();
        UIFont uiFont = UIFont.FromName(iosName, font.size());
        if (uiFont != null) return uiFont;

        if (iosName.equals(IOSFont.defaultFont().iosName())) {
            log.warning("Font shenanigans, default font not found!", "font", font);
            return null;
        }

        // font not found, use the default font at the given size, and style
        return getUIFont(new IOSFont(IOSFont.defaultFont().iosName(), font.style(), font.size()));
    }

    public void activate (IOSNativeTextField field) {
        _activeFields.put(field._field, field);
        _overlay.Add(field._field);
    }

    public void deactivate (IOSNativeTextField field) {
        field._field.RemoveFromSuperview();
        _activeFields.remove(field._field);
    }

    public boolean isAdded (UITextField field) {
        return field.IsDescendantOfView(_overlay);
    }

    protected final UIView _overlay;
    protected final Map<UITextField, IOSNativeTextField> _activeFields =
        new HashMap<UITextField, IOSNativeTextField>();

    // we specifically track whether we've transformed the game view in a boolean because
    // CGAffineTransform is a value class and cannot be null
    protected static boolean _gameViewTransformed;
    protected static CGAffineTransform _gameViewTransform;
}