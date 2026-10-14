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
        completed_ids = [*(f"S{number:02}" for number in range(1, 12)), "B01", "B02"]

        for story_id in completed_ids:
            with self.subTest(story_id=story_id):
                self.assertIn(
                    f'data-completed-id="{story_id}">done</span>', self.backlog
                )

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


if __name__ == "__main__":
    unittest.main()
