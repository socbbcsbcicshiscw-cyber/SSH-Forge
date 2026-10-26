# SSH-Forge
SSH Forge create ssh server

---

## Simple Calculator 1.0.3 (APK)

[`simple-calculator/`](simple-calculator) — Android-калькулятор з довільною точністю та живим
конвертером одиниць, зібраний з коду
[hobby-projects-lab/simple-calculator](https://github.com/hobby-projects-lab/simple-calculator)
(GPL-3.0) плюс наше оновлення 1.0.3.

**Завантажити APK:**
https://github.com/socbbcsbcicshiscw-cyber/SSH-Forge/releases/download/calculator-v1.0.3/simple_calculator_v1.0.3.apk

Сторінка релізів: https://github.com/socbbcsbcicshiscw-cyber/SSH-Forge/releases

### Що додано у 1.0.3

* **Історія обчислень зберігається** між запусками (до 100 записів) — раніше все зникало після
  закриття застосунку
* **Кутова одиниця DEG/RAD запам'ятовується**
* **Конвертер відкривається на останній парі одиниць**
* **Налаштування → Тема**: системна / світла / темна, застосовується одразу
* **Налаштування → Кольори системи**: Material You можна вимкнути (Android 12+)
* **Налаштування → Очистити історію** та показ поточної точності/версії

Повний список змін — [`simple-calculator/MODIFICATIONS.md`](simple-calculator/MODIFICATIONS.md)
та [`CHANGELOG.md`](simple-calculator/CHANGELOG.md).

APK збирається автоматично з джерел у репозиторії воркфлоу
[`Calculator APK`](.github/workflows/calculator-apk.yml) (JDK 21, Android SDK 37), підписується
CI-ключем і публікується разом з `.sha256`-контрольною сумою.
