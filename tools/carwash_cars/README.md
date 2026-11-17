# Спрайты машинок «Автомойки игрушек»

Исходники: «5 Free Toy Cars» (Popyca Studio, CC0), GLB. В репозиторий не кладутся.

1. Положить `*.glb`, `three.min.js` (r128) и `page.html` в один каталог, поднять `python3 -m http.server 8765`.
2. `node render.js` — рендерит в боковом виде кузов без колёс и по одному колесу (переднее/заднее) + `meta.json`.
3. `python3 build.py <каталог_с_рендерами> app/src/main/res/drawable-nodpi` — делает обводку, пишет `cw3_<id>_{body,wf,wr}.webp` и печатает таблицу для `CW_CARS` в `CarWashRoomScreen.kt`.
