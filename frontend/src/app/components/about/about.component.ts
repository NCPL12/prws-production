import { Component } from '@angular/core';

@Component({
  selector: 'about',
  standalone: true,
  imports: [],
  templateUrl: './about.component.html',
  styleUrl: './about.component.css'
})
export class AboutComponent {

  aboutHeading: string = 'About Us';
  aboutDescription1: string = `Neptune Controls Pvt Ltd is proud to present Version 1.0 of our state-of-the-art 
  application. This system is specifically designed to help industrial units monitor and manage key factors 
  like temperature and humidity, providing detailed reports and graphical insights.`;
  
  aboutDescription2: string = `By offering real-time data and analytics, our application empowers businesses to 
  optimize energy usage, reduce costs, and work towards sustainable industrial processes. With this solution, 
  Neptune Controls aims to make factories more efficient and eco-friendly.`;
  
  userGuideLinkText: string = 'Click here to view User Guide';
  userGuideLinkUrl: string = 'https://morth.nic.in/sites/default/files/dd12-13_0.pdf';

}
