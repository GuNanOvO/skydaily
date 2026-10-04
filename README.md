[![光遇每日任务 · 2026-10-05](https://skydaily.nankki.com/preview/daily.webp?v=2026-10-05-f272428600dfb624)](https://skydaily.nankki.com/)

# 光遇每日任务

每日任务、天气与日历，提供最近 30 天的历史记录。

## API

服务地址：`https://skydaily.nankki.com`。详见 [API 使用说明](https://skydaily.nankki.com/api.html)。

```sh
curl https://skydaily.nankki.com/v1/daily

curl https://skydaily.nankki.com/v1/daily/2026-10-03

curl https://skydaily.nankki.com/v1/daily/tasks
curl https://skydaily.nankki.com/v1/daily/weather
curl https://skydaily.nankki.com/v1/daily/calendar
curl https://skydaily.nankki.com/v1/daily/candles
```

返回 JSON，包含日期、结构化内容与数据更新时间。历史记录保留最近 30 天。服务支持跨域访问。

## 版权与反馈

游戏图文来源于游戏内「小精灵」，版权归原权利人所有。本站内容仅供学习与交流；获取最新资讯与完整攻略，请优先使用游戏内「小精灵」。

版权问题、侵权删除请求及其他反馈，请提交至 [仓库 Issues](https://github.com/GuNanOvO/skydaily/issues)，附相关内容链接与权利说明。
