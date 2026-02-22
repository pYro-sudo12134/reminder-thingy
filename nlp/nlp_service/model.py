import spacy
from spacy.language import Language
from spacy.tokens import Doc, Span
from typing import List, Dict, Optional, Tuple
import dateparser
from datetime import datetime, timedelta
import re
import json
from dataclasses import dataclass
from enum import Enum
import langdetect
from croniter import croniter
import pytz

class ReminderType(Enum):
    ABSOLUTE = "absolute"
    RELATIVE = "relative"
    RECURRING = "recurring"
    EVENT = "event"
    UNSPECIFIED = "unspecified"

@dataclass
class TimeExpression:
    type: ReminderType
    datetime: Optional[datetime] = None
    relative_seconds: Optional[int] = None
    cron_expression: Optional[str] = None
    natural_language: str = ""
    confidence: float = 0.0

@dataclass
class ParsedReminder:
    raw_text: str
    normalized_text: str
    action: str
    time_expression: TimeExpression
    entities: List[Dict]
    language: str
    confidence: float
    intent: str

class ReminderNLPModel:
    def __init__(self, model_dir: str = None):
        self.models = {}
        self.patterns = self._load_patterns()
        self._init_models()

    def _init_models(self):
        try:
            self.models['ru'] = spacy.load("ru_core_news_md")
            self._add_custom_pipes(self.models['ru'], 'ru')
        except:
            self.models['ru'] = spacy.blank('ru')
            self._add_custom_pipes(self.models['ru'], 'ru')

        try:
            self.models['en'] = spacy.load("en_core_web_md")
            self._add_custom_pipes(self.models['en'], 'en')
        except:
            self.models['en'] = spacy.blank('en')
            self._add_custom_pipes(self.models['en'], 'en')

    def _add_custom_pipes(self, nlp: Language, lang: str):
        pass

    def _load_patterns(self) -> Dict:
        return {
            'ru': {
                'time_patterns': [],
                'action_verbs': [
                    "напомни", "напомнить", "скажи", "посмотри", "позвони",
                    "сделай", "запиши", "купи", "отправь", "проверь"
                ],
                'time_units': {
                    'минут': 60,
                    'час': 3600,
                    'часа': 3600,
                    'день': 86400,
                    'дня': 86400,
                    'неделю': 604800,
                    'месяц': 2592000
                }
            },
            'en': {
                'time_patterns': [],
                'action_verbs': [
                    "remind", "tell", "call", "send", "buy",
                    "do", "check", "watch", "meet", "finish"
                ],
                'time_units': {
                    'minute': 60,
                    'minutes': 60,
                    'hour': 3600,
                    'hours': 3600,
                    'day': 86400,
                    'days': 86400,
                    'week': 604800,
                    'weeks': 604800,
                    'month': 2592000
                }
            }
        }

    def detect_language(self, text: str) -> str:
        try:
            lang = langdetect.detect(text)
            if lang in ['ru', 'en']:
                return lang
        except:
            pass
        return 'en'

    def preprocess(self, text: str, lang: str) -> str:
        text = re.sub(r'\s+', ' ', text).strip()
        text = text.lower()
        return text

    def parse(self, text: str, language: str = None) -> ParsedReminder:
        if not language:
            language = self.detect_language(text)

        normalized_text = self.preprocess(text, language)
        nlp = self.models.get(language, self.models['en'])
        doc = nlp(normalized_text)

        time_expr = self._extract_time_expression(doc, normalized_text, language)
        action = self._extract_action_simple(doc, normalized_text, time_expr, language)
        entities = self._extract_entities(doc)
        intent = self._classify_intent(doc, language)
        confidence = self._calculate_confidence(doc, time_expr, action)

        return ParsedReminder(
            raw_text=text,
            normalized_text=normalized_text,
            action=action,
            time_expression=time_expr,
            entities=entities,
            language=language,
            confidence=confidence,
            intent=intent
        )

    def _extract_time_expression(self, doc, text: str, lang: str) -> TimeExpression:
        print(f"Extracting time expression from: '{text}'")

        time_phrases = self._extract_time_phrases_from_text(text, lang)
        print(f"Found time phrases: {time_phrases}")

        for phrase in time_phrases:
            print(f"Processing phrase: '{phrase}'")

            if self._is_day_of_week_phrase(phrase, lang):
                print(f"Recognized day of week phrase: '{phrase}'")
                day_date = self._parse_day_of_week(phrase, lang)
                if day_date:
                    return TimeExpression(
                        type=ReminderType.ABSOLUTE,
                        datetime=day_date,
                        natural_language=phrase,
                        confidence=0.7
                    )

            try:
                settings = {
                    'RELATIVE_BASE': datetime.now(pytz.UTC),
                    'TIMEZONE': 'UTC',
                    'DATE_ORDER': 'DMY' if lang == 'ru' else 'MDY',
                    'PREFER_DATES_FROM': 'future'
                }

                parsed_date = dateparser.parse(
                    phrase,
                    languages=[lang[:2]],
                    settings=settings
                )

                print(f"Parsed date: {parsed_date}")

                if parsed_date:
                    if parsed_date > datetime.now():
                        return TimeExpression(
                            type=ReminderType.ABSOLUTE,
                            datetime=parsed_date,
                            natural_language=phrase,
                            confidence=0.8
                        )
                    else:
                        print(f"Date in past: {parsed_date}")
                        if self._contains_day_of_week(phrase, lang):
                            next_date = self._get_next_day_of_week(phrase, lang)
                            if next_date:
                                return TimeExpression(
                                    type=ReminderType.ABSOLUTE,
                                    datetime=next_date,
                                    natural_language=phrase,
                                    confidence=0.7
                                )
            except Exception as e:
                print(f"Error parsing phrase '{phrase}': {e}")
                continue

        relative_time = self._parse_relative_time(text, lang)
        if relative_time:
            print(f"Found relative time: {relative_time.natural_language}")
            return relative_time

        time_keywords = {
            'ru': ['сегодня', 'завтра', 'послезавтра'],
            'en': ['today', 'tomorrow', 'day after tomorrow']
        }

        for keyword in time_keywords.get(lang, []):
            if keyword in text.lower():
                print(f"Found keyword time expression: {keyword}")
                now = datetime.now()
                if keyword in ['завтра', 'tomorrow']:
                    target_date = now + timedelta(days=1)
                elif keyword in ['послезавтра', 'day after tomorrow']:
                    target_date = now + timedelta(days=2)
                else:
                    target_date = now

                return TimeExpression(
                    type=ReminderType.ABSOLUTE,
                    datetime=datetime(target_date.year, target_date.month, target_date.day, 9, 0),
                    natural_language=keyword,
                    confidence=0.6
                )

        print("No time found, using fallback")
        return TimeExpression(
            type=ReminderType.RELATIVE,
            relative_seconds=3600,
            natural_language="через 1 час" if lang == 'ru' else "in 1 hour",
            confidence=0.1
        )

    def _is_day_of_week_phrase(self, phrase: str, lang: str) -> bool:
        day_patterns = {
            'ru': [
                r'в\s+(понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)',
                r'\b(понедельник|вторник|среда|четверг|пятница|суббота|воскресенье)\b',
            ],
            'en': [
                r'on\s+(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)',
                r'\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b',
            ]
        }

        patterns = day_patterns.get(lang, [])
        for pattern in patterns:
            if re.search(pattern, phrase, re.IGNORECASE):
                return True
        return False

    def _contains_day_of_week(self, phrase: str, lang: str) -> bool:
        return self._is_day_of_week_phrase(phrase, lang)

    def _parse_day_of_week(self, phrase: str, lang: str) -> Optional[datetime]:
        days_mapping = {
            'ru': {
                'понедельник': 0, 'вторник': 1, 'среду': 2, 'среда': 2,
                'четверг': 3, 'пятницу': 4, 'пятница': 4,
                'субботу': 5, 'суббота': 5, 'воскресенье': 6
            },
            'en': {
                'monday': 0, 'tuesday': 1, 'wednesday': 2,
                'thursday': 3, 'friday': 4, 'saturday': 5, 'sunday': 6
            }
        }

        phrase_lower = phrase.lower()
        mapping = days_mapping.get(lang, {})

        for day_name, day_num in mapping.items():
            if day_name in phrase_lower:
                now = datetime.now()
                current_weekday = now.weekday()

                days_ahead = day_num - current_weekday
                if days_ahead <= 0:
                    days_ahead += 7

                target_date = now + timedelta(days=days_ahead)
                target_date = datetime(target_date.year, target_date.month, target_date.day, 9, 0)

                if 'morning' in phrase_lower:
                    target_date = datetime(target_date.year, target_date.month, target_date.day, 9, 0)
                elif 'afternoon' in phrase_lower:
                    target_date = datetime(target_date.year, target_date.month, target_date.day, 14, 0)
                elif 'evening' in phrase_lower:
                    target_date = datetime(target_date.year, target_date.month, target_date.day, 18, 0)
                elif 'night' in phrase_lower:
                    target_date = datetime(target_date.year, target_date.month, target_date.day, 20, 0)

                return target_date

        return None

    def _get_next_day_of_week(self, phrase: str, lang: str) -> Optional[datetime]:
        return self._parse_day_of_week(phrase, lang)

    def _extract_time_phrases_from_text(self, text: str, lang: str) -> List[str]:
        time_patterns = {
            'ru': [
                r'завтра в \d{1,2}[:\.]\d{2}',
                r'сегодня в \d{1,2}[:\.]\d{2}',
                r'послезавтра в \d{1,2}[:\.]\d{2}',
                r'в \d{1,2}[:\.]\d{2}',
                r'\bзавтра\b',
                r'\bсегодня\b',
                r'\bпослезавтра\b',
                r'\d{1,2}\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)',
                r'в\s+(понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)',
                r'\b(понедельник|вторник|среда|четверг|пятница|суббота|воскресенье)\b',
                r'через\s+\d+\s+(минут[уы]?|час[а]?|день|дня|недел[юи]|неделю|месяц[а]?)',
            ],
            'en': [
                r'tomorrow at \d{1,2}(?:[:\.]\d{2})?\s*(?:AM|PM)?',
                r'today at \d{1,2}(?:[:\.]\d{2})?\s*(?:AM|PM)?',
                r'at \d{1,2}(?:[:\.]\d{2})?\s*(?:AM|PM)?',
                r'\btomorrow\b',
                r'\btoday\b',
                r'\bday after tomorrow\b',
                r'\d{1,2}\s+(January|February|March|April|May|June|July|August|September|October|November|December)',
                r'on\s+(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)',
                r'\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b',
                r'this\s+(morning|afternoon|evening|night)',
                r'\btonight\b',
                r'in\s+\d+\s+(minute[s]?|hour[s]?|day[s]?|week[s]?|month[s]?)',
                r'next\s+(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)',
            ]
        }

        patterns = time_patterns.get(lang, [])
        found_phrases = []

        for pattern in patterns:
            matches = re.finditer(pattern, text, re.IGNORECASE)
            for match in matches:
                found_phrases.append(match.group(0))

        return found_phrases

    def _parse_relative_time(self, text: str, lang: str) -> Optional[TimeExpression]:
        patterns = {
            'ru': [
                (r'через\s+(\d+)\s+(минут[уы]?|час[а]?|день|дня|недел[юи]|неделю|месяц[а]?)', 1, 2),
                (r'(\d+)\s+(минут[уы]?|час[а]?|день|дня)\s+(?:спустя|позже)', 1, 2),
                (r'спустя\s+(\d+)\s+(минут[уы]?|час[а]?|день|дня)', 1, 2),
            ],
            'en': [
                (r'in\s+(\d+)\s+(minute[s]?|hour[s]?|day[s]?|week[s]?|month[s]?)', 1, 2),
                (r'(\d+)\s+(minute[s]?|hour[s]?|day[s]?|week[s]?)\s+(?:from now|later)', 1, 2),
                (r'after\s+(\d+)\s+(minute[s]?|hour[s]?|day[s]?)', 1, 2),
            ]
        }

        lang_patterns = patterns.get(lang, [])
        time_units = self.patterns.get(lang, {}).get('time_units', {})

        for pattern, amount_idx, unit_idx in lang_patterns:
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                try:
                    amount = int(match.group(amount_idx))
                    unit_str = match.group(unit_idx).lower()

                    multiplier = None
                    for unit_key, seconds in time_units.items():
                        if unit_str.startswith(unit_key):
                            multiplier = seconds
                            break

                    if multiplier:
                        seconds = amount * multiplier

                        if lang == 'ru':
                            desc = f"через {amount} {unit_str}"
                        else:
                            desc = f"in {amount} {unit_str}"

                        return TimeExpression(
                            type=ReminderType.RELATIVE,
                            relative_seconds=seconds,
                            natural_language=desc,
                            confidence=0.85
                        )
                except:
                    continue

        return None

    def _extract_action_simple(self, doc, text: str, time_expr: TimeExpression, lang: str) -> str:
        print(f"\nExtracting action from: '{text}'")

        if time_expr.natural_language:
            text_without_time = text.replace(time_expr.natural_language, '')
            print(f"Text without time: '{text_without_time}'")
        else:
            text_without_time = text

        command_patterns = {
            'ru': [
                r'напомни\s+(?:мне\s+)?(?:о\s+)?(?:в\s+)?',
                r'не\s+забудь\s+(?:о\s+)?(?:в\s+)?',
                r'скажи\s+(?:мне\s+)?(?:о\s+)?',
                r'доведи\s+(?:до\s+)?',
                r'посмотри\s+(?:мне\s+)?'
            ],
            'en': [
                r'remind\s+(?:me\s+)?(?:to\s+)?(?:about\s+)?',
                r'don\'t\s+forget\s+(?:to\s+)?(?:about\s+)?',
                r'tell\s+(?:me\s+)?(?:to\s+)?',
                r'notify\s+(?:me\s+)?',
                r'alert\s+(?:me\s+)?'
            ]
        }

        action_text = text_without_time
        patterns = command_patterns.get(lang, [])
        for pattern in patterns:
            action_text = re.sub(pattern, '', action_text, flags=re.IGNORECASE)

        print(f"After removing command: '{action_text}'")

        filler_words = {
            'ru': ['пожалуйста', 'нужно', 'надо', 'чтобы', 'мне', 'должен', 'следовало'],
            'en': ['please', 'need', 'to', 'that', 'me', 'should', 'would']
        }

        words = filler_words.get(lang, [])
        for word in words:
            pattern = rf'\b{re.escape(word)}\b'
            action_text = re.sub(pattern, '', action_text, flags=re.IGNORECASE)

        print(f"After removing filler: '{action_text}'")

        action_text = re.sub(r'\s+', ' ', action_text).strip()
        action_text = re.sub(r'^[,\s\.:;]+|[,\s\.:;]+$', '', action_text)

        print(f"After cleaning: '{action_text}'")

        if len(action_text) < 3 or action_text.lower() in ['', ' ', 'напомни', 'remind']:
            print("Action text too short, using fallback")
            action_text = self._extract_verb_or_noun(doc, lang)
            print(f"Fallback action: '{action_text}'")

        return action_text if action_text else ("напоминание" if lang == 'ru' else "reminder")

    def _extract_verb_or_noun(self, doc, lang: str) -> str:
        important_words = []

        for token in doc:
            if token.text.lower() in ['напомни', 'remind', 'скажи', 'tell']:
                continue

            if token.pos_ in ['VERB', 'NOUN', 'PROPN'] and len(token.text) > 2:
                important_words.append(token.text)

        if important_words:
            return ' '.join(important_words[:3])

        return "напоминание" if lang == 'ru' else "reminder"

    def _extract_entities(self, doc) -> List[Dict]:
        entities = []
        for ent in doc.ents:
            entities.append({
                'text': ent.text,
                'label': ent.label_,
                'start': ent.start_char,
                'end': ent.end_char
            })
        return entities

    def _classify_intent(self, doc, lang: str) -> str:
        text = doc.text.lower()

        intent_keywords = {
            'reminder': {
                'ru': ['напомни', 'напоминание', 'не забудь'],
                'en': ['remind', 'reminder', 'don\'t forget']
            },
            'task': {
                'ru': ['запиши', 'выполни', 'задание'],
                'en': ['do', 'complete', 'task']
            },
            'meeting': {
                'ru': ['встреча', 'собрание', 'совещание'],
                'en': ['meeting', 'call', 'appointment']
            },
            'birthday': {
                'ru': ['день рождения', 'др'],
                'en': ['birthday', 'bday']
            }
        }

        for intent, keywords in intent_keywords.items():
            lang_keywords = keywords.get(lang, [])
            for keyword in lang_keywords:
                if keyword in text:
                    return intent

        return 'reminder'

    def _calculate_confidence(self, doc, time_expr: TimeExpression, action: str) -> float:
        confidence = 0.5
        
        if time_expr.type == ReminderType.ABSOLUTE and time_expr.datetime:
            confidence += 0.3
        
        default_actions = ['напоминание', 'reminder']
        if action not in default_actions:
            confidence += 0.1
        
        if len(doc.text.split()) > 3:
            confidence += 0.1
        
        return min(max(confidence, 0.1), 0.95)