"""Contract tests for the assessment backlog document."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BACKLOG = ROOT / "assesment" / "docs" / "backlog.html"


class BacklogContractTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.backlog = BACKLOG.read_text(encoding="utf-8")

    def test_completed_delivery_stories_have_visible_done_pills(self):
        completed_ids = [*(f"S{number:02}" for number in range(1, 12)), "B01", "B03"]

        for story_id in completed_ids:
            with self.subTest(story_id=story_id):
                self.assertIn(
                    f'data-completed-id="{story_id}">done</span>', self.backlog
                )

    def test_each_story_has_at_most_one_done_pill(self):
        stories = re.findall(
            r'<details id="[^"]+"[^>]*data-story[^>]*>.*?</details>',
            self.backlog,
            re.DOTALL,
        )

        for story in stories:
            story_id = re.search(r'<details id="([^"]+)"', story).group(1)
            with self.subTest(story_id=story_id):
                self.assertLessEqual(story.count('class="done-pill"'), 1)

    def test_developer_follow_up_contains_ten_sdd_tdd_stories(self):
        section = self.backlog.split('<section id="developer-follow-up">', 1)[1]
        section = section.split("</section>", 1)[0]

        self.assertEqual(
            [f"DF{number:02}" for number in range(1, 11)],
            re.findall(r'<details id="(DF\d{2})"', section),
        )
        self.assertEqual(10, section.count('data-follow-up-story'))
        self.assertEqual(10, section.count("Acceptatiecriteria · Given / When / Then"))
        self.assertEqual(10, section.count("01 · Red"))
        self.assertEqual(10, section.count("02 · Green"))
        self.assertEqual(10, section.count("03 · Refactor"))
        self.assertEqual(10, section.count("Gereed wanneer"))

    def test_completed_technical_follow_ups_are_recorded_on_applicable_stories(self):
        for story_id in ("S04", "S05", "S07", "S08", "S10", "B01"):
            with self.subTest(story_id=story_id):
                self.assertIn(
                    f"<strong>Implementatiestatus {story_id}.</strong>", self.backlog
                )

        developer_follow_up = self.backlog.split(
            '<section id="developer-follow-up">', 1
        )[1].split("</section>", 1)[0]
        self.assertNotIn('class="done-pill"', developer_follow_up)


if __name__ == "__main__":
    unittest.main()
