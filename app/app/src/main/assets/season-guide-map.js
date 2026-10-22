(function (mapName) {
    "use strict";

    var STATUS_SELECTED = "selected";
    var STATUS_ALREADY = "already-selected";
    var STATUS_NOT_READY = "not-ready";
    var STATUS_NOT_FOUND = "not-found";

    var win = typeof window !== "undefined" ? window : null;

    function result(status, reason, extra) {
        var out = { status: status };
        if (reason) out.reason = reason;
        if (extra) {
            for (var key in extra) {
                if (Object.prototype.hasOwnProperty.call(extra, key)) out[key] = extra[key];
            }
        }
        return out;
    }

    /** A map label matches an element name exactly, or as an explicit "label-suffix" role name. */
    function matchesLabel(value, label) {
        if (typeof value !== "string") return false;
        var name = value.trim();
        if (name === label) return true;
        return name.indexOf(label + "-") === 0;
    }

    function baseUrl(value) {
        if (typeof value !== "string") return "";
        var cut = value.indexOf("?");
        return (cut < 0 ? value : value.slice(0, cut)).trim();
    }

    function isPageIndex(value, count) {
        return typeof value === "number" && value % 1 === 0 && value >= 0 && value < count;
    }

    /**
     * Resolve the unique page reached by a "jump" event for the label.
     * Any conflicting target means the label is ambiguous and must not be clicked.
     */
    function resolveTarget(pages, label) {
        var targets = Object.create(null);
        var uuids = [];
        var matched = 0;
        for (var i = 0; i < pages.length; i++) {
            var elements = pages[i] && pages[i].elements;
            if (!elements || typeof elements.length !== "number") continue;
            for (var j = 0; j < elements.length; j++) {
                var element = elements[j];
                if (!element || typeof element !== "object") continue;
                if (!matchesLabel(element.name, label)) continue;
                var event = element.events;
                if (!event || event.type !== "jump") continue;
                var target = event.value;
                if (typeof target !== "string" || target === "") continue;
                matched++;
                if (!targets[target]) {
                    targets[target] = true;
                    uuids.push(target);
                }
            }
        }
        if (matched === 0) return null;
        if (uuids.length !== 1) return { ambiguous: true, targets: uuids };
        for (var k = 0; k < pages.length; k++) {
            if (pages[k] && pages[k].uuid === uuids[0]) {
                return { uuid: uuids[0], index: k, matches: matched };
            }
        }
        return { missing: true, targets: uuids };
    }

    /** Vue keeps the live page index on the render root; bounded walk, no data evaluation. */
    function vueActiveIndex(count) {
        if (!win || !win.document) return null;
        var host = win.document.querySelector("#app");
        var vm = host && host.__vue__;
        if (!vm) return null;
        var queue = [vm];
        var visited = 0;
        while (queue.length > 0 && visited < 256) {
            var component = queue.shift();
            visited++;
            if (!component) continue;
            var data = component.$data;
            if (data && isPageIndex(data.currentPageIndex, count)) return data.currentPageIndex;
            var children = component.$children;
            if (!children || typeof children.length !== "number") continue;
            for (var i = 0; i < children.length; i++) queue.push(children[i]);
        }
        return null;
    }

    /** The official slide disables mouse on touch devices; keep both input modes active. */
    function prepareMouse() {
        var host = win.document && win.document.querySelector("#app");
        var queue = host && host.__vue__ ? [host.__vue__] : [];
        for (var visited = 0; queue.length && visited < 256; visited++) {
            var component = queue.shift();
            if (!component) continue;
            var slide = component.$slide;
            var handler = slide && slide.scroller && slide.scroller.actionsHandler;
            if (handler && handler.options && typeof handler.rebindDOMEvents === "function") {
                if (handler.options.disableMouse) {
                    slide.options.disableMouse = false;
                    handler.options.disableMouse = false;
                    handler.rebindDOMEvents();
                }
                return true;
            }
            var children = component.$children || [];
            for (var i = 0; i < children.length; i++) queue.push(children[i]);
        }
        return false;
    }

    function slidePages() {
        if (!win || !win.document) return [];
        return win.document.querySelectorAll(".slide-page");
    }

    function slideBackground(slide) {
        var value = slide && slide.style && slide.style.backgroundImage;
        if (!value) return "";
        var match = /^url\(["']?(.*?)["']?\)$/.exec(value.trim());
        return match ? baseUrl(match[1]) : "";
    }

    function hasRenderedContent(slide) {
        return !!slide && (slide.querySelector("img") !== null || slide.querySelector("[ns-event-label]") !== null);
    }

    /** Map a slide DOM node back to its metadata page by page background image. */
    function slidePage(pages, index) {
        var slides = slidePages();
        if (index < 0 || index >= slides.length) return null;
        var style = pages[index] && pages[index].commonStyle;
        var background = style && typeof style.backgroundImage === "string" ? baseUrl(style.backgroundImage) : "";
        if (background) {
            for (var i = 0; i < slides.length; i++) {
                if (slideBackground(slides[i]) === background) return slides[i];
            }
        }
        return slides[index] || null;
    }

    /** Fallback when the framework instance is unavailable: the sole populated slide is active. */
    function domActiveIndex(pages) {
        var slides = slidePages();
        var active = -1;
        var found = 0;
        for (var i = 0; i < slides.length; i++) {
            if (!hasRenderedContent(slides[i])) continue;
            var index = -1;
            for (var p = 0; p < pages.length; p++) {
                if (slidePage(pages, p) === slides[i]) {
                    index = p;
                    break;
                }
            }
            if (index >= 0) {
                active = index;
                found++;
            }
        }
        return found === 1 ? active : null;
    }

    function activeIndex(pages) {
        var index = vueActiveIndex(pages.length);
        if (index !== null) return index;
        return domActiveIndex(pages);
    }

    function elementUuids(page, label, targetUuid) {
        var uuids = Object.create(null);
        var elements = page && page.elements;
        if (!elements || typeof elements.length !== "number") return uuids;
        for (var i = 0; i < elements.length; i++) {
            var element = elements[i];
            if (!element || typeof element !== "object") continue;
            if (!matchesLabel(element.name, label)) continue;
            var event = element.events;
            if (!event || event.type !== "jump" || event.value !== targetUuid) continue;
            if (typeof element.uuid === "string") uuids[element.uuid] = true;
        }
        return uuids;
    }

    function imageUrls(page, label, targetUuid) {
        var urls = Object.create(null);
        var elements = page && page.elements;
        if (!elements || typeof elements.length !== "number") return urls;
        for (var i = 0; i < elements.length; i++) {
            var element = elements[i];
            if (!element || typeof element !== "object") continue;
            if (!matchesLabel(element.name, label)) continue;
            var event = element.events;
            if (!event || event.type !== "jump" || event.value !== targetUuid) continue;
            var props = element.propsValue;
            var url = props && baseUrl(props.imageSrc);
            if (url) urls[url] = true;
        }
        return urls;
    }

    function visible(node) {
        if (!node || !node.getBoundingClientRect) return false;
        var rect = node.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    }

    function clickAncestor(node) {
        var current = node;
        for (var depth = 0; depth < 8 && current; depth++) {
            if (current.getAttribute && current.getAttribute("ns-on") === "click") return current;
            current = current.parentElement;
        }
        current = node;
        for (var fallback = 0; fallback < 8 && current; fallback++) {
            if (current.getAttribute && current.getAttribute("ns-event-label")) return current;
            current = current.parentElement;
        }
        return node;
    }

    /** Prefer the semantic tracking label (element uuid); fall back to metadata image URL. */
    function findEventNode(pages, index, label, targetUuid) {
        var page = pages[index];
        var uuids = elementUuids(page, label, targetUuid);
        var urls = imageUrls(page, label, targetUuid);
        var scope = slidePage(pages, index);
        var roots = scope ? [scope] : [];
        if (!scope) {
            var doc = win && win.document;
            if (doc) roots.push(doc);
        }
        var candidates = [];
        for (var r = 0; r < roots.length; r++) {
            var labelled = roots[r].querySelectorAll("[ns-event-label]");
            for (var i = 0; i < labelled.length; i++) {
                var raw = labelled[i].getAttribute("ns-event-label");
                if (!raw) continue;
                var meta;
                try {
                    meta = JSON.parse(raw);
                } catch (error) {
                    meta = null;
                }
                if (!meta || !meta.uuid || !uuids[meta.uuid]) continue;
                candidates.push(labelled[i]);
            }
        }
        var best = null;
        for (var c = 0; c < candidates.length; c++) {
            if (visible(candidates[c])) return candidates[c];
            if (!best) best = candidates[c];
        }
        if (best) return best;

        var images = [];
        for (var s = 0; s < roots.length; s++) {
            var found = roots[s].querySelectorAll("img");
            for (var n = 0; n < found.length; n++) images.push(found[n]);
        }
        var imageBest = null;
        for (var m = 0; m < images.length; m++) {
            var image = images[m];
            var src = baseUrl(image.currentSrc || image.src);
            var lazy = baseUrl(image.getAttribute && image.getAttribute("data-src"));
            if (!(src && urls[src]) && !(lazy && urls[lazy])) continue;
            if (visible(image)) return clickAncestor(image);
            if (!imageBest) imageBest = image;
        }
        return imageBest ? clickAncestor(imageBest) : null;
    }

    /** Match the guide's touch/mouse mode so its scroll component forwards the click. */
    function tap(node) {
        var rect = node.getBoundingClientRect();
        var x = rect.left + rect.width / 2;
        var y = rect.top + rect.height / 2;
        var types = ["mousedown", "mouseup", "click"];
        try {
            if ("ontouchstart" in win && typeof win.Touch === "function" && typeof win.TouchEvent === "function") {
                var touch = new win.Touch({
                    identifier: 1,
                    target: node,
                    clientX: x,
                    clientY: y,
                    pageX: x + win.scrollX,
                    pageY: y + win.scrollY
                });
                var touchTypes = ["touchstart", "touchend"];
                for (var t = 0; t < touchTypes.length; t++) {
                    var active = t === 0 ? [touch] : [];
                    node.dispatchEvent(new win.TouchEvent(touchTypes[t], {
                        bubbles: true,
                        cancelable: true,
                        touches: active,
                        targetTouches: active,
                        changedTouches: [touch]
                    }));
                }
                return true;
            }
            for (var i = 0; i < types.length; i++) {
                var event;
                if (win && typeof win.MouseEvent === "function") {
                    event = new win.MouseEvent(types[i], {
                        bubbles: true,
                        cancelable: true,
                        view: win,
                        button: 0,
                        clientX: x,
                        clientY: y
                    });
                } else {
                    var doc = node.ownerDocument;
                    event = doc.createEvent("MouseEvents");
                    event.initMouseEvent(types[i], true, true, win, 0, x, y, x, y, false, false, false, false, 0, null);
                }
                node.dispatchEvent(event);
            }
            return true;
        } catch (error) {
            try {
                node.click();
                return true;
            } catch (clickError) {
                return false;
            }
        }
    }

    if (!win) return result(STATUS_NOT_READY, "no-window");

    if (!prepareMouse()) return result(STATUS_NOT_READY, "input-not-ready");
    var label = typeof mapName === "string" ? mapName.trim() : "";
    if (!label) return result(STATUS_NOT_FOUND, "empty-map");

    var data = win.__initData;
    var pages = data && data.pages;
    if (!pages || typeof pages.length !== "number" || pages.length === 0) {
        return result(STATUS_NOT_READY, "no-metadata");
    }

    var target = resolveTarget(pages, label);
    if (!target) return result(STATUS_NOT_FOUND, "no-map-element", { map: label });
    if (target.ambiguous) return result(STATUS_NOT_FOUND, "ambiguous-map", { map: label, targets: target.targets });
    if (target.missing) return result(STATUS_NOT_FOUND, "target-page-missing", { map: label });
    var targetUuid = target.uuid;

    var current = activeIndex(pages);
    if (current === null) return result(STATUS_NOT_READY, "active-page-unknown", { map: label });
    if (current === target.index) {
        return result(STATUS_ALREADY, null, { map: label, target: targetUuid, pageIndex: target.index });
    }

    var node = findEventNode(pages, current, label, targetUuid);
    if (!node) return result(STATUS_NOT_READY, "click-node-missing", { map: label, target: targetUuid });

    if (!tap(node)) return result(STATUS_NOT_READY, "click-failed", { map: label, target: targetUuid });

    // The live guide switches pages a few frames after the tap; a synchronous
    // re-read only reports "selected" when the target is already verified.
    var after = activeIndex(pages);
    if (after === target.index) {
        return result(STATUS_SELECTED, null, { map: label, target: targetUuid, pageIndex: target.index });
    }
    return result(STATUS_NOT_READY, "jump-pending", {
        map: label,
        target: targetUuid,
        pageIndex: target.index,
        clicked: true
    });
})
