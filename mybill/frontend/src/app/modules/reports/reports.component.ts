import { Component, ElementRef, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { gsap } from 'gsap';

@Component({
  selector: 'app-reports',
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.scss']
})
export class ReportsComponent implements OnInit {
  selectedTabIndex = 0;

  constructor(
    private host: ElementRef<HTMLElement>,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const tabParam = this.route.snapshot.queryParamMap.get('tab');
    if (tabParam === 'small-client' || tabParam === '3') {
      this.selectedTabIndex = 3;
    } else if (tabParam === 'cash-booking' || tabParam === '2') {
      this.selectedTabIndex = 2;
    } else if (tabParam === 'collection' || tabParam === '1') {
      this.selectedTabIndex = 1;
    } else if (tabParam === '0') {
      this.selectedTabIndex = 0;
    } else {
      const savedTab = sessionStorage.getItem('reports_selected_tab');
      if (savedTab !== null) {
        const parsed = parseInt(savedTab, 10);
        if (!isNaN(parsed) && parsed >= 0 && parsed <= 3) {
          this.selectedTabIndex = parsed;
        }
      }
    }
  }

  onTabChange(index: number): void {
    this.selectedTabIndex = index;
    sessionStorage.setItem('reports_selected_tab', String(index));
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    const card = this.host.nativeElement.querySelector('.reports-card');
    if (card) {
      gsap.fromTo(
        card,
        { boxShadow: '0 18px 50px rgba(15, 23, 42, 0.12)' },
        { boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', duration: 0.35, ease: 'power2.out' }
      );
    }
  }
}
