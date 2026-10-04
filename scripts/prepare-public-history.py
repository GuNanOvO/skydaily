import argparse
import importlib.util
import json
import re
from datetime import date, timedelta
from pathlib import Path
from urllib.parse import urlsplit

spec = importlib.util.spec_from_file_location('compressor', Path(__file__).with_name('compress-history-images.py'))
compressor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compressor)
guide_policy = json.loads((Path(__file__).resolve().parent.parent / 'src/link-policy.json').read_text())['seasonGuide']

def normalize_guide(value):
    try:
        url = urlsplit(value)
        origin = urlsplit(guide_policy['origin'])
        if (url.scheme == origin.scheme and url.hostname == origin.hostname and url.port in (None, 443)
                and not url.username and not url.password and re.fullmatch(guide_policy['pathPattern'], url.path)):
            return guide_policy['origin'] + url.path.rstrip('/') + '/'
    except ValueError:
        pass
    return None


def clean(value):
    if isinstance(value, str):
        return re.sub(r'https?://[^\s<>"\u0027）)]+', '', value).strip()
    if isinstance(value, list):
        return [clean(item) for item in value]
    if isinstance(value, dict):
        return {key: clean(item) for key, item in value.items()}
    return value


def prepare(root, site, snapshot=None):
    root = Path(root)
    base = site.rstrip('/') + '/history/'
    if urlsplit(base).scheme != 'https':
        raise ValueError('site must use HTTPS')
    (root / 'media').mkdir(parents=True, exist_ok=True)
    (root / 'daily').mkdir(parents=True, exist_ok=True)
    media = {}
    if snapshot:
        data = json.loads(Path(snapshot).read_text())
        date.fromisoformat(data['date'])
        if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', data['date']) or data['errors'] or len(data['data']['tasks']) != 4:
            raise ValueError('snapshot is not complete')
        target = root / 'daily' / (data['date'] + '.json')
        previous = json.loads(target.read_text()) if target.exists() else None
        if not previous or previous['meta']['fetched_at'] <= data['meta']['fetched_at']:
            target.write_text(json.dumps(data, ensure_ascii=False))
    files = sorted((root / 'daily').glob('*.json'))
    if not files:
        raise ValueError('no archive records')
    newest = date.fromisoformat(files[-1].stem)
    cutoff = newest - timedelta(days=29)
    used = set()
    entries = []
    for file in files:
        if date.fromisoformat(file.stem) < cutoff:
            file.unlink()
            continue
        envelope = json.loads(file.read_text())
        groups = envelope['data']['taskDetails'] + envelope['data']['candles'] + [envelope['data']['weather'], envelope['data']['calendar']]
        images_by_group = []
        links_by_group = []
        for group in groups:
            links = []
            if group and group.get('keyword') == guide_policy['keyword']:
                for value in group.get('links', []):
                    canonical = normalize_guide(value)
                    if canonical and canonical not in links:
                        links.append(canonical)
                if group.get('links') and not links:
                    raise ValueError('unsupported_guide_link')
            links_by_group.append(links)
            if group is None:
                images_by_group.append([])
                continue
            images = []
            for url in group['images']:
                item = media.get(url)
                if not item:
                    
                    name = urlsplit(url).path.split('/')[-1]
                    local = root / 'media' / name
                    parsed_url = urlsplit(url)
                    archive_url = urlsplit(base)
                    if (parsed_url.scheme == archive_url.scheme and parsed_url.netloc == archive_url.netloc
                            and parsed_url.path == archive_url.path + 'media/' + name
                            and not parsed_url.query and not parsed_url.fragment
                            and re.fullmatch(r'[a-f0-9]{24}\.webp', name) and local.is_file()):
                        item = {'status': 'ok', 'file': 'media/' + name}
                    else:
                        item = compressor.compress(url, root)
                    media[url] = item
                if item['status'] == 'ok':
                    images.append(base + item['file'])
                    used.add(item['file'])
                elif {'section': 'media', 'code': 'image_unavailable'} not in envelope['errors']:
                    envelope['errors'].append({'section': 'media', 'code': 'image_unavailable'})
            images_by_group.append(images)
        envelope = clean(envelope)
        groups = envelope['data']['taskDetails'] + envelope['data']['candles'] + [envelope['data']['weather'], envelope['data']['calendar']]
        for group, images, links in zip(groups, images_by_group, links_by_group):
            if group is None:
                continue
            group['images'] = images
            if 'links' in group:
                group['links'] = links
                group['videos'] = []
        file.write_text(json.dumps(envelope, ensure_ascii=False, indent=2) + '\n')
        entries.append({'date': envelope['date'], 'file': 'daily/' + file.name})
    for file in (root / 'media').glob('*'):
        if 'media/' + file.name not in used:
            file.unlink()
    (root / 'index.json').write_text(json.dumps({'from': entries[0]['date'], 'to': entries[-1]['date'], 'date_count': len(entries), 'dates': entries}, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', default='history')
    parser.add_argument('--site', required=True)
    parser.add_argument('--snapshot')
    args = parser.parse_args()
    prepare(args.directory, args.site, args.snapshot)
