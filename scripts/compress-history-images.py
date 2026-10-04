import hashlib
import io
import time
from urllib.parse import urlsplit
from urllib.request import Request, HTTPRedirectHandler, build_opener
from PIL import Image, ImageOps

def allowed_url(value):
    url = urlsplit(value)
    return url.scheme == 'https' and url.hostname == 'ok.166.net' and not url.username and not url.password and url.port in (None, 443)

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
            return {'url': url, 'status': 'ok', 'file': 'media/' + name,
                    'bytes': path.stat().st_size, 'width': image.width, 'height': image.height, 'reused': True}
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
                return {'url': url, 'status': 'ok', 'file': 'media/' + name,
                        'original_bytes': len(raw), 'bytes': path.stat().st_size,
                        'width': image.width, 'height': image.height,
                        'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
        except Exception as exc:
            error = str(exc)
            if '404' in error or '410' in error:
                break
            time.sleep(attempt + 1)
    return {'url': url, 'status': 'failed', 'error': error}
