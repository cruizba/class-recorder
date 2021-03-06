package com.classrecorder.teacherserver.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;


import com.classrecorder.teacherserver.server.entities.Course;
import com.classrecorder.teacherserver.server.entities.Student;
import com.classrecorder.teacherserver.server.entities.Teacher;
import com.classrecorder.teacherserver.server.repository.CourseRepository;
import com.classrecorder.teacherserver.server.repository.StudentRepository;
import com.classrecorder.teacherserver.server.repository.TeacherRepository;
import com.classrecorder.teacherserver.server.repository.VideoRepository;
import com.classrecorder.teacherserver.server.services.FfmpegService;
import com.classrecorder.teacherserver.server.services.YoutubeService;

@Controller
public class Initializer implements CommandLineRunner {

	private final Logger log = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private FfmpegService ffmpeg;
	
	@Autowired
	private YoutubeService youtubeService;
	
	@Autowired
	private TeacherRepository teacherRepository;
	
	@Autowired
	private StudentRepository studentRepository;
	
	@Autowired
	private CourseRepository courseRepository;
	
	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private Environment environment;
	
	boolean recording;
	
	private boolean isDevEnv() {
		for(String env: this.environment.getActiveProfiles()) {
			return env.equals("dev");
        }
		return false;
	}

	@Override
	public void run(String... args) throws Exception {
		if(teacherRepository.count() == 0 && isDevEnv()) {

			log.info("Inserting example data");
			Teacher teacher1 = new Teacher("Teacher1", "1234", "Juan Rodriguez", "juan@juan.com", "ROLE_TEACHER");
			Teacher teacher2 = new Teacher("Teacher2", "1234", "Alberto Ruiz", "alberto@alberto.com","ROLE_TEACHER");
			Teacher teacher3 = new Teacher("Teacher3", "1234", "Juan Pérez", "juan_perez@juan.com", "ROLE_TEACHER");
			Student student1 = new Student("Student1", "1234", "Rick Sanchez", "rick@rick.com", "ROLE_STUDENT");
			Student student2 = new Student("Student2", "1234", "Gonzalo Alvarez", "gonzalo@gonzalo.com", "ROLE_STUDENT");

			Course course = new Course("Curso de Programación", "Esto es un curso de programación");
			Course course3 = new Course("Curso Python", "Esto es un curso de Python. El texto representa una descripcion.");
			Course course2 = new Course("Curso del teacher 3", "Otro curso");

			//Teachers and courses
			teacher1.getCoursesCreated().add(course);
			teacher2.getCoursesCreated().add(course);
			course.getTeacherCreators().add(teacher1);
			course.getTeacherCreators().add(teacher2);

			teacher3.getCoursesCreated().add(course2);
			course2.getTeacherCreators().add(teacher3);

			teacher1.getCoursesCreated().add(course3);
			course3.getTeacherCreators().add(teacher1);

			//Subscribers
			student1.getSubscribed().add(course);
			student1.getSubscribed().add(course2);
			course.getSubscribers().add(student1);
			course2.getSubscribers().add(student2);

			teacherRepository.save(teacher1);
			teacherRepository.save(teacher2);
			teacherRepository.save(teacher3);
			studentRepository.save(student1);
			studentRepository.save(student2);
		}
		
	}
}
