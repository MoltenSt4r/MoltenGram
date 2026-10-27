package app.exteraless.clip;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline Cyrillic/Russian concept translator for on-device OpenCLIP / MobileCLIP search.
 * MobileCLIP-S0 was trained on English datasets with an English BPE vocabulary.
 * This maps common Russian vocabulary (animals, objects, vehicles, nature, landscapes,
 * food, colors, emotions, weather) to aligned English concepts before inference.
 */
public class RussianConceptTranslator {

    private static final Map<String, String> DICTIONARY = new HashMap<>(512);
    private static final Pattern CYRILLIC_PATTERN = Pattern.compile("[а-яА-ЯёЁ]");
    private static final Pattern WORD_PATTERN = Pattern.compile("[а-яА-ЯёЁa-zA-Z0-9]+");

    static {
        // --- Animals ---
        put(new String[]{"кот", "котик", "кошка", "котята", "котенок", "коты", "кошки", "кошечка", "киса", "котейка"}, "cat kitten");
        put(new String[]{"собака", "пес", "песик", "собачка", "щенок", "щенки", "собаки", "псы", "песель", "собакен"}, "dog puppy");
        put(new String[]{"птица", "птичка", "птицы", "птички", "птенец"}, "bird");
        put(new String[]{"рыба", "рыбка", "рыбы", "рыбки"}, "fish");
        put(new String[]{"медведь", "медвежонок", "мишка", "медведи"}, "bear");
        put(new String[]{"лиса", "лисица", "лисенок", "лисы"}, "fox");
        put(new String[]{"волк", "волчонок", "волки"}, "wolf");
        put(new String[]{"заяц", "кролик", "зайчик", "зайки", "зайцы", "кролики"}, "rabbit bunny hare");
        put(new String[]{"лошадь", "конь", "жеребенок", "лошади", "кони"}, "horse");
        put(new String[]{"корова", "теленок", "коровы", "бык", "быки"}, "cow bull");
        put(new String[]{"овца", "баран", "ягненок", "овцы", "бараны"}, "sheep lamb");
        put(new String[]{"свинья", "поросенок", "свиньи", "хрюшка"}, "pig piglet");
        put(new String[]{"тигр", "тигренок", "тигры"}, "tiger");
        put(new String[]{"лев", "львенок", "львы"}, "lion");
        put(new String[]{"слон", "слоненок", "слоны"}, "elephant");
        put(new String[]{"обезьяна", "мартышка", "обезьяны"}, "monkey");
        put(new String[]{"хомяк", "хомячок", "хомяки"}, "hamster");
        put(new String[]{"енот", "еноты"}, "raccoon");
        put(new String[]{"олень", "олени"}, "deer");
        put(new String[]{"попугай", "попугаи", "попугайчик"}, "parrot");
        put(new String[]{"утка", "утенок", "утки"}, "duck");
        put(new String[]{"гусь", "гуси"}, "goose");
        put(new String[]{"лебедь", "лебеди"}, "swan");
        put(new String[]{"змея", "змеи"}, "snake");
        put(new String[]{"лягушка", "жаба", "лягушки"}, "frog toad");
        put(new String[]{"бабочка", "бабочки"}, "butterfly");
        put(new String[]{"паук", "пауки"}, "spider");
        put(new String[]{"дельфин", "дельфины"}, "dolphin");
        put(new String[]{"кит", "киты"}, "whale");
        put(new String[]{"акула", "акулы"}, "shark");
        put(new String[]{"черепаха", "черепахи"}, "turtle tortoise");
        put(new String[]{"еж", "ежик", "ежи"}, "hedgehog");
        put(new String[]{"белка", "белочка", "белки"}, "squirrel");

        // --- Objects ---
        put(new String[]{"телефон", "смартфон", "мобильник", "телефоны", "смартфоны"}, "phone smartphone mobile");
        put(new String[]{"компьютер", "пк", "компы", "компьютеры"}, "computer pc");
        put(new String[]{"ноутбук", "ноут", "ноутбуки"}, "laptop");
        put(new String[]{"планшет", "планшеты"}, "tablet");
        put(new String[]{"монитор", "экран", "мониторы", "дисплей"}, "monitor screen display");
        put(new String[]{"клавиатура", "клава", "клавиатуры"}, "keyboard");
        put(new String[]{"мышь", "мышка", "мыши"}, "computer mouse");
        put(new String[]{"книга", "книжка", "книги"}, "book");
        put(new String[]{"тетрадь", "тетради", "блокнот", "блокноты"}, "notebook notepad");
        put(new String[]{"ручка", "ручки"}, "pen");
        put(new String[]{"карандаш", "карандаши"}, "pencil");
        put(new String[]{"часы", "часики"}, "watch clock");
        put(new String[]{"очки"}, "glasses sunglasses eyeglasses");
        put(new String[]{"кружка", "чашка", "кружки", "чашки", "бокал"}, "mug cup glass");
        put(new String[]{"тарелка", "тарелки", "блюдце"}, "plate dish");
        put(new String[]{"бутылка", "бутылочка", "бутылки"}, "bottle");
        put(new String[]{"стол", "столик", "столы"}, "table desk");
        put(new String[]{"стул", "табурет", "кресло", "стулья", "кресла"}, "chair armchair");
        put(new String[]{"кровать", "кровати", "постель"}, "bed");
        put(new String[]{"диван", "диваны"}, "sofa couch");
        put(new String[]{"дверь", "двери"}, "door");
        put(new String[]{"окно", "окна"}, "window");
        put(new String[]{"сумка", "сумочка", "сумки"}, "bag purse");
        put(new String[]{"рюкзак", "рюкзаки"}, "backpack");
        put(new String[]{"кошелек", "кошельки", "бумажник"}, "wallet");
        put(new String[]{"ключ", "ключи", "брелок"}, "key keys");
        put(new String[]{"нож", "ножик", "ножи"}, "knife");
        put(new String[]{"ножницы"}, "scissors");
        put(new String[]{"лампа", "светильник", "люстра", "лампы"}, "lamp light lighting");
        put(new String[]{"телевизор", "телик", "тв", "телевизоры"}, "tv television");
        put(new String[]{"гитара", "гитары"}, "guitar");
        put(new String[]{"мяч", "мячик", "мячи"}, "ball");
        put(new String[]{"наушники"}, "headphones earphones");
        put(new String[]{"камера", "фотоаппарат", "камеры"}, "camera photo");
        put(new String[]{"картина", "постер", "рисунок"}, "painting picture art poster");
        put(new String[]{"зеркало", "зеркала"}, "mirror");
        put(new String[]{"одежда", "шмотки"}, "clothes clothing");
        put(new String[]{"футболка", "футболки", "майка"}, "tshirt t-shirt shirt");
        put(new String[]{"рубашка", "рубашки"}, "shirt");
        put(new String[]{"куртка", "пальто", "куртки"}, "jacket coat");
        put(new String[]{"джинсы", "штаны", "брюки"}, "jeans pants trousers");
        put(new String[]{"платье", "платья", "юбка"}, "dress skirt");
        put(new String[]{"обувь", "ботинки", "кроссовки", "туфли"}, "shoes sneakers boots");
        put(new String[]{"шапка", "кепка", "шляпа"}, "hat cap");

        // --- Vehicles ---
        put(new String[]{"машина", "тачка", "авто", "автомобиль", "машины", "автомобили"}, "car automobile vehicle");
        put(new String[]{"грузовик", "грузовики", "фура", "фуры"}, "truck");
        put(new String[]{"автобус", "автобусы"}, "bus");
        put(new String[]{"поезд", "поезда", "электричка", "электрички"}, "train");
        put(new String[]{"трамвай", "трамваи"}, "tram streetcar");
        put(new String[]{"самолет", "самолеты"}, "airplane plane aircraft");
        put(new String[]{"вертолет", "вертолеты"}, "helicopter");
        put(new String[]{"корабль", "корабли", "судно"}, "ship boat vessel");
        put(new String[]{"лодка", "катер", "лодки", "катера", "яхта"}, "boat yacht");
        put(new String[]{"велосипед", "велик", "велосипеды"}, "bicycle bike");
        put(new String[]{"мотоцикл", "мотик", "мотоциклы"}, "motorcycle motorbike");
        put(new String[]{"скутер", "самокат", "скутеры", "самокаты"}, "scooter");
        put(new String[]{"такси"}, "taxi cab");
        put(new String[]{"метро"}, "metro subway");
        put(new String[]{"трактор", "тракторы"}, "tractor");

        // --- Nature & Landscapes ---
        put(new String[]{"природа", "пейзаж"}, "nature landscape scenery");
        put(new String[]{"лес", "лесок", "леса"}, "forest woods");
        put(new String[]{"дерево", "деревья", "деревце"}, "tree trees");
        put(new String[]{"трава", "травка", "газон"}, "grass lawn");
        put(new String[]{"цветок", "цветочек", "цветы", "цветочки"}, "flower flowers");
        put(new String[]{"роза", "розы"}, "rose roses");
        put(new String[]{"ромашка", "ромашки"}, "daisy chamomile");
        put(new String[]{"лист", "листок", "листик", "листья"}, "leaf leaves");
        put(new String[]{"гора", "горка", "горы"}, "mountain mountains hill");
        put(new String[]{"река", "речка", "реки"}, "river stream");
        put(new String[]{"озеро", "озера"}, "lake pond");
        put(new String[]{"море", "моря"}, "sea");
        put(new String[]{"океан", "океаны"}, "ocean");
        put(new String[]{"пляж", "пляжи"}, "beach coast shore");
        put(new String[]{"песок"}, "sand");
        put(new String[]{"небо", "небеса"}, "sky");
        put(new String[]{"облако", "туча", "облака", "тучи"}, "cloud clouds");
        put(new String[]{"солнце", "солнышко"}, "sun sunshine");
        put(new String[]{"луна", "месяц"}, "moon");
        put(new String[]{"звезда", "звездочка", "звезды"}, "star stars");
        put(new String[]{"закат", "закаты"}, "sunset sundown");
        put(new String[]{"рассвет", "рассветы"}, "sunrise dawn");
        put(new String[]{"водопад", "водопады"}, "waterfall");
        put(new String[]{"поле", "поля"}, "field meadow");
        put(new String[]{"парк", "парки"}, "park");
        put(new String[]{"сад", "садик", "сады"}, "garden");
        put(new String[]{"дорога", "тропа", "тропинка", "дороги", "тропинки", "шоссе"}, "road path trail highway");
        put(new String[]{"город", "улица", "города", "улицы"}, "city street town urban");
        put(new String[]{"здание", "дом", "здания", "дома"}, "building house architecture");
        put(new String[]{"мост", "мосты"}, "bridge");

        // --- Food & Drinks ---
        put(new String[]{"еда", "пища", "блюдо"}, "food meal dish");
        put(new String[]{"пицца"}, "pizza");
        put(new String[]{"бургер", "гамбургер", "бургеры"}, "burger hamburger");
        put(new String[]{"суши", "роллы"}, "sushi rolls");
        put(new String[]{"хлеб", "булка", "батон"}, "bread");
        put(new String[]{"сыр", "сыры"}, "cheese");
        put(new String[]{"мясо", "стейк", "шашлык"}, "meat steak bbq");
        put(new String[]{"курица", "цыпленок"}, "chicken");
        put(new String[]{"суп", "супы", "борщ"}, "soup");
        put(new String[]{"салат", "салаты"}, "salad");
        put(new String[]{"яблоко", "яблочко", "яблоки"}, "apple apples");
        put(new String[]{"банан", "бананы"}, "banana bananas");
        put(new String[]{"апельсин", "апельсины", "мандарин", "мандарины"}, "orange tangerine citrus");
        put(new String[]{"лимон", "лимоны"}, "lemon");
        put(new String[]{"клубника", "земляника"}, "strawberry");
        put(new String[]{"арбуз", "дыня"}, "watermelon melon");
        put(new String[]{"торт", "тортик", "торты"}, "cake");
        put(new String[]{"пирог", "пирожок", "пироги", "пирожное", "десерт"}, "pie pastry dessert");
        put(new String[]{"мороженое", "пломбир"}, "ice cream");
        put(new String[]{"шоколад", "шоколадка", "конфета", "конфеты"}, "chocolate candy");
        put(new String[]{"кофе", "капучино", "латте", "эспрессо"}, "coffee cappuccino latte");
        put(new String[]{"чай", "чаек"}, "tea");
        put(new String[]{"вода", "водичка"}, "water drink");
        put(new String[]{"сок", "соки"}, "juice");
        put(new String[]{"молоко"}, "milk");
        put(new String[]{"пиво"}, "beer");
        put(new String[]{"вино"}, "wine");
        put(new String[]{"картошка", "картофель", "фри"}, "potato french fries");
        put(new String[]{"макароны", "паста"}, "pasta noodles");
        put(new String[]{"рис"}, "rice");
        put(new String[]{"яйцо", "яичница", "яйца"}, "egg eggs omelet");
        put(new String[]{"фрукты", "фрукт"}, "fruit fruits");
        put(new String[]{"овощи", "овощ"}, "vegetable vegetables");

        // --- Colors ---
        put(new String[]{"красный", "красная", "красное", "красные"}, "red");
        put(new String[]{"синий", "синяя", "синее", "синие"}, "blue");
        put(new String[]{"голубой", "голубая", "голубое", "голубые"}, "light blue azure");
        put(new String[]{"зеленый", "зеленая", "зеленое", "зеленые"}, "green");
        put(new String[]{"желтый", "желтая", "желтое", "желтые"}, "yellow");
        put(new String[]{"оранжевый", "оранжевая", "оранжевое", "оранжевые"}, "orange");
        put(new String[]{"фиолетовый", "фиолетовая", "фиолетовое", "фиолетовые", "сиреневый"}, "purple violet");
        put(new String[]{"розовый", "розовая", "розовое", "розовые"}, "pink");
        put(new String[]{"черный", "черная", "черное", "черные"}, "black");
        put(new String[]{"белый", "белая", "белое", "белые"}, "white");
        put(new String[]{"серый", "серая", "серое", "серые"}, "grey gray");
        put(new String[]{"коричневый", "коричневая", "коричневое", "коричневые"}, "brown");
        put(new String[]{"золотой", "золотая", "золотое", "золотые"}, "gold golden");
        put(new String[]{"серебряный", "серебряная", "серебряное", "серебряные"}, "silver");

        // --- Emotions ---
        put(new String[]{"радость", "веселье", "восторг"}, "joy happiness delight");
        put(new String[]{"счастье"}, "happiness");
        put(new String[]{"грусть", "печаль", "тоска"}, "sadness grief sorrow");
        put(new String[]{"злость", "гнев", "ярость"}, "anger rage mad");
        put(new String[]{"любовь"}, "love heart romantic");
        put(new String[]{"страх", "ужас"}, "fear scared terror");
        put(new String[]{"удивление"}, "surprise amazed shock");
        put(new String[]{"улыбка", "улыбки"}, "smile smiling");
        put(new String[]{"смех"}, "laugh laughter laughing");
        put(new String[]{"слезы"}, "tears crying cry");
        put(new String[]{"спокойствие", "покой"}, "calm peaceful peace");
        put(new String[]{"радостный", "радостная", "радостные", "веселый", "веселая", "веселые"}, "happy joyful cheerful");
        put(new String[]{"счастливый", "счастливая", "счастливые"}, "happy smiling");
        put(new String[]{"грустный", "грустная", "грустные", "печальный", "печальная", "печальные"}, "sad unhappy");
        put(new String[]{"злой", "злая", "злые", "сердитый", "сердитая", "сердитые"}, "angry mad");
        put(new String[]{"смешной", "смешная", "смешные", "забавный", "забавная", "забавные"}, "funny hilarious cute");
        put(new String[]{"милый", "милая", "милые", "милота"}, "cute adorable lovely");
        put(new String[]{"красивый", "красивая", "красивое", "красивые", "красота"}, "beautiful pretty gorgeous scenic");

        // --- Weather ---
        put(new String[]{"погода"}, "weather");
        put(new String[]{"дождь", "дождик", "ливень", "дожди"}, "rain raining shower rainy");
        put(new String[]{"снег", "снежок", "снегопад", "метель", "сугроб", "сугробы"}, "snow snowfall snowy blizzard winter");
        put(new String[]{"гроза", "молния", "буря", "шторм"}, "thunderstorm storm lightning");
        put(new String[]{"ветер", "ветерок"}, "wind windy breezy");
        put(new String[]{"туман"}, "fog foggy mist misty");
        put(new String[]{"мороз", "холод", "зима"}, "frost cold freezing winter");
        put(new String[]{"жара", "зной", "тепло", "лето"}, "hot heat warm summer sunny");
        put(new String[]{"облачно", "пасмурно"}, "cloudy overcast");
        put(new String[]{"солнечно", "ясно"}, "sunny clear bright");
        put(new String[]{"лед", "ледок", "льдина", "льдины"}, "ice icy frozen");
        put(new String[]{"радуга"}, "rainbow");
    }

    private static void put(String[] keys, String value) {
        for (String key : keys) {
            DICTIONARY.put(key, value);
        }
    }

    public static boolean containsCyrillic(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        return CYRILLIC_PATTERN.matcher(text).find();
    }

    /**
     * Translates Russian/Cyrillic words in a query to corresponding English concepts.
     * If no Russian words or no mappings are found, returns the original query.
     */
    public static String translateToEnglishConcepts(String text) {
        if (TextUtils.isEmpty(text) || !containsCyrillic(text)) {
            return text;
        }

        String lower = text.toLowerCase(Locale.ROOT).trim();
        // Check full phrase match first
        String fullMatch = DICTIONARY.get(lower);
        if (fullMatch != null) {
            return fullMatch;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = WORD_PATTERN.matcher(lower);
        boolean translatedAny = false;

        while (matcher.find()) {
            String word = matcher.group();
            String concept = DICTIONARY.get(word);
            if (concept != null) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(concept);
                translatedAny = true;
            } else {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(word);
            }
        }

        if (translatedAny && result.length() > 0) {
            return result.toString();
        }
        return text;
    }
}
