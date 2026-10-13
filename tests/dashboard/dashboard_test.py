"""Contract tests for the browser-based assessment dashboard."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
INDEX = ROOT / "dashboard" / "index.html"
NGINX = ROOT / "dashboard" / "nginx.conf"
DOCKERFILE = ROOT / "dashboard" / "Dockerfile"
COMPOSE = ROOT / "compose.yaml"
README = ROOT / "README.md"


class DashboardContractTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.index = INDEX.read_text(encoding="utf-8")
        cls.nginx = NGINX.read_text(encoding="utf-8")
        cls.dockerfile = DOCKERFILE.read_text(encoding="utf-8")
        cls.compose = COMPOSE.read_text(encoding="utf-8")
        cls.readme = README.read_text(encoding="utf-8")

    def test_compose_exposes_dashboard_on_port_8082_after_both_apis(self):
        dashboard = self.compose.split("  dashboard:", 1)[1]

        self.assertIn('- "8082:8080"', dashboard)
        self.assertRegex(dashboard, r"depends_on:\s+lease-api:\s+condition: service_started")
        self.assertRegex(
            dashboard,
            r"depends_on:[\s\S]*interest-rate-service:\s+condition: service_started",
        )

    def test_page_has_one_action_and_terminal_for_each_documented_step(self):
        for step in range(1, 9):
            with self.subTest(step=step):
                self.assertIn(f'id="step-{step}"', self.index)
                self.assertIn(f'data-step="{step}"', self.index)
                self.assertIn(f'id="output-{step}"', self.index)

        self.assertEqual(8, len(re.findall(r'data-step="[1-8]"', self.index)))

    def test_page_contains_navigation_and_business_form_styling(self):
        self.assertIn("https://cdn.tailwindcss.com", self.index)
        self.assertIn("assesment/docs/test-run.md", self.index)
        self.assertEqual(8, self.index.count('href="#top"'))

    def test_page_uses_htmx_for_declarative_actions(self):
        self.assertIn(
            "https://unpkg.com/htmx.org@2.0.8/dist/htmx.min.js", self.index
        )
        self.assertIn('hx-on:click="runAllSteps(this)"', self.index)
        for step in range(1, 9):
            with self.subTest(step=step):
                self.assertIn(
                    f'hx-on:click="runSingleStep({step})"', self.index
                )
        self.assertNotIn('querySelectorAll(".run-step")', self.index)
        self.assertNotIn("addEventListener", self.index)

    def test_step_cards_stack_on_mobile_and_split_action_from_terminal_on_desktop(self):
        cards = re.findall(
            r'<section id="step-[1-8]"[^>]*data-layout="step-card"[^>]*>',
            self.index,
        )

        self.assertEqual(8, len(cards))
        for card in cards:
            self.assertIn("grid", card)
            self.assertIn("lg:grid-cols-[minmax(16rem,0.75fr)_minmax(0,1.5fr)]", card)
        self.assertEqual(8, self.index.count('data-panel="action"'))
        self.assertEqual(8, self.index.count('data-panel="terminal"'))

    def test_browser_calls_same_origin_reverse_proxies(self):
        self.assertIn('const LEASE_API = "/lease-api"', self.index)
        self.assertIn('const INTEREST_API = "/interest-api"', self.index)
        self.assertNotIn("http://localhost:8080", self.index)
        self.assertNotIn("http://localhost:8081", self.index)
        self.assertRegex(self.nginx, r"location /lease-api/\s*\{")
        self.assertIn("proxy_pass http://lease-api:8080/;", self.nginx)
        self.assertRegex(self.nginx, r"location /interest-api/\s*\{")
        self.assertIn("proxy_pass http://interest-rate-service:8080/;", self.nginx)

    def test_page_keeps_tokens_and_created_ids_for_later_steps(self):
        for state_name in (
            "brokerToken",
            "employeeToken",
            "customerId",
            "vehicleId",
            "rateId",
            "rateDate",
        ):
            with self.subTest(state_name=state_name):
                self.assertRegex(self.index, rf"\b{state_name}\b")

    def test_page_renders_http_status_and_json_output(self):
        self.assertIn("response.status", self.index)
        self.assertIn("JSON.stringify", self.index)
        self.assertIn("application/problem+json", self.index)
        self.assertEqual(47, self.index.count("await request(step"))

    def test_readme_points_to_the_running_dashboard(self):
        self.assertIn("http://localhost:8082", self.readme)
        self.assertIn("API-testdashboard", self.readme)

    def test_dashboard_uses_a_pinned_non_root_image(self):
        self.assertRegex(
            self.dockerfile,
            r"FROM nginxinc/nginx-unprivileged:1\.27-alpine@sha256:[0-9a-f]{64}",
        )


if __name__ == "__main__":
    unittest.main()
