"""Run with: python3 -m unittest discover -s tools/geo -p 'test_*.py'."""
import unittest

from nature_names import NatureNames


class NatureNamesTest(unittest.TestCase):
    def test_same_name_and_bad_wikidata_link_do_not_change_other_volcano(self):
        review = NatureNames()
        shared = dict(name='La Soufrière', name_en='La Soufrière', wikidataid='Q26544',
                      name_zh='圣文森特苏弗里耶尔火山', name_zht='聖文森特蘇弗里耶爾火山')
        guadeloupe = review.names(dict(shared, ne_id=1159108393))
        saint_vincent = review.names(dict(shared, ne_id=1159108403))
        self.assertEqual(guadeloupe['zh'], '苏弗里耶尔火山')
        self.assertEqual(saint_vincent['zh'], '圣文森特苏弗里耶尔火山')

    def test_script_conversion_preserves_regional_wording_and_other_languages(self):
        p = dict(NAME='New Zealand', NAME_EN='New Zealand', NAME_KO='뉴질랜드',
                 NAME_JA='ニュージーランド', NAME_ZH='新西兰', NAME_ZHT='紐西蘭')
        names = NatureNames().names(p)
        self.assertEqual(names, dict(en='New Zealand', ko='뉴질랜드', ja='ニュージーランド',
                                     zh='新西兰', zht='紐西蘭'))

    def test_mixed_scripts_and_transliteration_characters(self):
        review = NatureNames()
        self.assertEqual(review.names(dict(name='Himalayas', name_zh='喜馬拉雅山脈',
                                           name_zht='喜马拉雅山脉'))['zht'], '喜馬拉雅山脈')
        names = review.names(dict(name='Kanchenjunga', name_zh='干城章嘉峰'))
        self.assertEqual(names['zh'], '干城章嘉峰')
        self.assertEqual(names['zht'], '干城章嘉峰')

    def test_missing_chinese_falls_back_without_inventing_a_translation(self):
        self.assertEqual(NatureNames().names(dict(name='Example', name_en='Example')),
                         {'en': 'Example'})

    def test_upstream_rename_requires_review(self):
        with self.assertRaisesRegex(ValueError, 'Upstream name changed'):
            NatureNames().names(dict(ne_id=1159104335, name='Unexpected new place'))

    def test_deleted_upstream_feature_does_not_silently_drop_a_correction(self):
        with self.assertRaisesRegex(ValueError, 'Unused Natural Earth overrides'):
            NatureNames().validate()


if __name__ == '__main__':
    unittest.main()
