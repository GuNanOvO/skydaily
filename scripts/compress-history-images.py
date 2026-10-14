import hashlib
import io
import time
from urllib.parse import urlsplit
from urllib.request import Request, HTTPRedirectHandler, build_opener
from PIL import Image, ImageOps

def allowed_url(value):
    url = urlsplit(value)
    return url.scheme == 'https' and url.hostname == 'ok.166.net' and not url.username and not url.password and url.port in (None, 443)

THUMB_WIDTH = 480
THUMB_QUALITY = 75


def thumb_path(root, name):
    return root / 'thumbs' / name


def write_thumb(root, name, image):
    target = thumb_path(root, name)
    target.parent.mkdir(parents=True, exist_ok=True)
    if image.width > THUMB_WIDTH:
        ratio = THUMB_WIDTH / image.width
        resized = image.resize((THUMB_WIDTH, max(1, round(image.height * ratio))), Image.LANCZOS)
        resized.save(target, 'WEBP', quality=THUMB_QUALITY, method=6)
    else:
        # 原图已经够小：直接复用字节，避免二次编码损失
        target.write_bytes((root / 'media' / name).read_bytes())
    return target


def ensure_thumb(root, name):
    """确保 thumbs/<name> 存在（对已归档的图片就地生成，幂等）。"""
    source = root / 'media' / name
    target = thumb_path(root, name)
    if not source.is_file():
        return None
    if target.exists() and target.stat().st_mtime >= source.stat().st_mtime:
        return {'file': 'thumbs/' + name, 'bytes': target.stat().st_size}
    with Image.open(source) as image:
        image.load()
        write_thumb(root, name, image)
    return {'file': 'thumbs/' + name, 'bytes': target.stat().st_size}

class ImageRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not allowed_url(newurl):
            raise ValueError('unsupported_image_redirect')
        return super().redirect_request(req, fp, code, msg, headers, newurl)

opener = build_opener(ImageRedirectHandler())


def compress(url, root):
    name = hashlib.sha256(url.encode()).hexdigest()[:24] + '.webp'
    path = root / 'media' / name
    if not allowed_url(url):
        return {'url': url, 'status': 'failed', 'error': 'unsupported_image_host'}
    if path.exists():
        with Image.open(path) as image:
            image.load()
            name_only = path.name
            thumb = write_thumb(root, name_only, image)
            return {'url': url, 'status': 'ok', 'file': 'media/' + name_only,
                    'bytes': path.stat().st_size, 'width': image.width, 'height': image.height, 'reused': True,
                    'thumb': 'thumbs/' + name_only, 'thumb_bytes': thumb.stat().st_size}
    error = ''
    for attempt in range(3):
        try:
            with opener.open(Request(url, headers={'User-Agent': 'SkyDaily-history-archive/1.0'}), timeout=25) as response:
                raw = response.read(30 * 1024 * 1024 + 1)
            if len(raw) > 30 * 1024 * 1024:
                raise ValueError('image_exceeds_30MiB')
            with Image.open(io.BytesIO(raw)) as image:
                frames = getattr(image, 'n_frames', 1)
                if frames > 1:
                    raise ValueError('animated_image_requires_review')
                image = ImageOps.exif_transpose(image)
                image = image.convert('RGBA' if 'A' in image.getbands() else 'RGB')
                image.save(path, 'WEBP', quality=85, method=6)
                thumb = write_thumb(root, name, image)
                return {'url': url, 'status': 'ok', 'file': 'media/' + name,
                        'original_bytes': len(raw), 'bytes': path.stat().st_size,
                        'width': image.width, 'height': image.height,
                        'thumb': 'thumbs/' + name, 'thumb_bytes': thumb.stat().st_size,
                        'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
        except Exception as exc:
            error = str(exc)
            if '404' in error or '410' in error:
                break
            time.sleep(attempt + 1)
    return {'url': url, 'status': 'failed', 'error': error}
